"""Capture only Android emulator output via EmulatorController.streamAudio.

QA-only dependency: grpcio. The discovery Bearer token remains in memory and is
never printed or written. No desktop or microphone APIs are used.
"""

from __future__ import annotations

import argparse
import array
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import time
import wave

import grpc


SDK = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or Path.home() / ".cache/newmybook-toolchain/android-sdk")
EVIDENCE_ROOT = Path(__file__).resolve().parents[1] / "current/1.0.2/followup-2026-09-30"
SERVICE = "/android.emulation.control.EmulatorController/"
RATE = 44100
CHANNELS = 2


def varint(value: int) -> bytes:
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value, shift = 0, 0
    while offset < len(data) and shift < 70:
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if not byte & 128:
            return value, offset
        shift += 7
    raise ValueError("Invalid protobuf varint")


def fields(data: bytes) -> dict[int, int | bytes]:
    result: dict[int, int | bytes] = {}
    offset = 0
    while offset < len(data):
        tag, offset = read_varint(data, offset)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            result[number], offset = read_varint(data, offset)
        elif wire == 2:
            length, offset = read_varint(data, offset)
            end = offset + length
            if end > len(data):
                raise ValueError("Truncated protobuf field")
            result[number] = data[offset:end]
            offset = end
        elif wire in (1, 5):
            offset += 8 if wire == 1 else 4
        else:
            raise ValueError("Unexpected protobuf wire type")
    return result


def current_user(adb: Path, serial: str) -> int:
    value = subprocess.check_output(
        [str(adb), "-s", serial, "shell", "am", "get-current-user"],
        text=True, timeout=5, stderr=subprocess.DEVNULL,
    ).strip()
    return int(value)


def guard(adb: Path, serial: str, expected: int) -> None:
    if current_user(adb, serial) != expected:
        raise RuntimeError("Public Android user guard failed; capture discarded")


def discovery(serial: str) -> dict[str, str]:
    serial_port = serial.removeprefix("emulator-")
    directory = Path(os.environ["LOCALAPPDATA"]) / "Temp/avd/running"
    for path in directory.glob("pid_*.ini"):
        data = dict(
            line.split("=", 1)
            for line in path.read_text(encoding="utf-8").splitlines()
            if "=" in line
        )
        if data.get("port.serial") == serial_port:
            if not data.get("grpc.token") or not data.get("grpc.port"):
                raise RuntimeError("Emulator discovery lacks local gRPC token/port")
            return data
    raise RuntimeError("No matching running emulator discovery found")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--android-user", type=int, required=True)
    parser.add_argument("--seconds", type=float, default=15)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--probe", action="store_true", help="Read-only getStatus; no audio stream")
    args = parser.parse_args()
    if args.android_user != 15:
        parser.error("This evidence helper is restricted to public isolated Android user 15")
    if not 1 <= args.seconds <= 60:
        parser.error("--seconds must be 1..60")
    if not args.probe:
        if args.output is None:
            parser.error("--output is required for capture")
        output = args.output.resolve()
        if not output.is_relative_to(EVIDENCE_ROOT.resolve()) or output.suffix.lower() != ".wav":
            parser.error("Output must be a .wav within followup-2026-09-30")
        if output.exists() or output.with_suffix(".json").exists():
            parser.error("Evidence output exists; choose a new name")
    adb = SDK / "platform-tools/adb.exe"
    guard(adb, args.serial, args.android_user)
    config = discovery(args.serial)
    metadata = (("authorization", "Bearer " + config["grpc.token"]),)
    with grpc.insecure_channel("localhost:" + config["grpc.port"]) as channel:
        if args.probe:
            rpc = channel.unary_unary(
                SERVICE + "getStatus", request_serializer=lambda x: x,
                response_deserializer=lambda x: x,
            )
            rpc(b"", timeout=3, metadata=metadata)
            print("gRPC getStatus OK; public Android user 15; no audio captured")
            return 0
        # AudioFormat: samplingRate=44100, channels=Stereo(1), format=S16(1).
        request = b"\x08" + varint(RATE) + b"\x10\x01\x18\x01"
        rpc = channel.unary_stream(
            SERVICE + "streamAudio", request_serializer=lambda x: x,
            response_deserializer=lambda x: x,
        )
        chunks: list[bytes] = []
        timestamps: list[int] = []
        status = "deadline_reached"
        started = time.monotonic()
        stream = rpc(request, timeout=args.seconds, metadata=metadata)
        try:
            for encoded in stream:
                # Check before retaining every audio packet. If the user changes,
                # discard the entire in-memory recording and write no evidence.
                guard(adb, args.serial, args.android_user)
                packet = fields(encoded)
                fmt = fields(packet.get(1, b""))
                if (fmt.get(1, 0), fmt.get(2, 0), fmt.get(3, 0)) != (RATE, 1, 1):
                    raise RuntimeError("Emulator returned an unexpected audio format")
                audio = packet.get(3, b"")
                if not isinstance(audio, bytes) or len(audio) % 4:
                    raise RuntimeError("Emulator returned malformed stereo S16 audio")
                chunks.append(audio)
                if isinstance(packet.get(2), int):
                    timestamps.append(packet[2])
        except grpc.RpcError as error:
            if error.code() != grpc.StatusCode.DEADLINE_EXCEEDED:
                raise RuntimeError("Emulator streamAudio failed: " + error.code().name) from None
        finally:
            stream.cancel()
        guard(adb, args.serial, args.android_user)
        elapsed = time.monotonic() - started
    pcm = b"".join(chunks)
    samples = array.array("h")
    samples.frombytes(pcm)
    if sys.byteorder != "little":
        samples.byteswap()
    nonzero = sum(sample != 0 for sample in samples)
    rms = math.sqrt(sum(sample * sample for sample in samples) / len(samples)) if samples else 0
    summary = {
        "source": "Android emulator EmulatorController.streamAudio",
        "android_user": args.android_user,
        "serial": args.serial,
        "format": "PCM signed 16-bit little endian stereo",
        "sampling_rate": RATE,
        "requested_wall_seconds": args.seconds,
        "elapsed_wall_seconds": round(elapsed, 3),
        "audio_seconds": round(len(pcm) / (RATE * CHANNELS * 2), 3),
        "packets": len(chunks),
        "pcm_bytes": len(pcm),
        "nonzero_samples": nonzero,
        "peak_amplitude": max((abs(sample) for sample in samples), default=0),
        "rms_amplitude": round(rms, 3),
        "pcm_sha256": hashlib.sha256(pcm).hexdigest(),
        "first_packet_timestamp_us": min(timestamps) if timestamps else None,
        "last_packet_timestamp_us": max(timestamps) if timestamps else None,
        "status": status if chunks else "no_audio_packets_before_deadline",
        "audible_speech_verified": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output), "wb") as wav:
        wav.setnchannels(CHANNELS)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(pcm)
    output.with_suffix(".json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False))
    print("WAV:", output)
    return 0 if nonzero else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, ValueError, subprocess.SubprocessError, OSError) as error:
        print("Capture failed:", str(error), file=sys.stderr)
        raise SystemExit(1)
