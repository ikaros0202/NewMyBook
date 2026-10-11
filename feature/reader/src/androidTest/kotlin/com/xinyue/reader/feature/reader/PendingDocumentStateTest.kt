package com.xinyue.reader.feature.reader

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.HandoffExportRequest
import com.xinyue.reader.core.ui.AnnotationExportRequestSaver
import com.xinyue.reader.core.ui.HandoffExportRequestSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingDocumentStateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pickerRequestsSurviveRecreationAndAreConsumedOnce() {
        val restoration = StateRestorationTester(compose)
        var receivedAnnotation: AnnotationExportRequest? = null
        var receivedHandoff: HandoffExportRequest? = null
        var receivedCover: String? = null
        var receivedCharset: String? = null
        val annotation = AnnotationExportRequest(AnnotationExportFormat.JSON, setOf("public-book"), true)
        val handoff = HandoffExportRequest("public-book", true)
        restoration.setContent {
            var pendingAnnotation by rememberSaveable(stateSaver = AnnotationExportRequestSaver) { mutableStateOf<AnnotationExportRequest?>(null) }
            var pendingHandoff by rememberSaveable(stateSaver = HandoffExportRequestSaver) { mutableStateOf<HandoffExportRequest?>(null) }
            var coverId by rememberSaveable { mutableStateOf<String?>(null) }
            var charset by rememberSaveable { mutableStateOf<String?>(null) }
            Column {
                Button(onClick = { pendingAnnotation = annotation; pendingHandoff = handoff; coverId = "public-book"; charset = "GB18030" }) { Text("Launch pickers") }
                Button(onClick = {
                    receivedAnnotation = pendingAnnotation; pendingAnnotation = null
                    receivedHandoff = pendingHandoff; pendingHandoff = null
                    receivedCover = coverId; coverId = null
                    receivedCharset = charset; charset = null
                }) { Text("Deliver results") }
            }
        }
        compose.onNodeWithText("Launch pickers").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Deliver results").performClick()
        assertEquals(annotation, receivedAnnotation)
        assertEquals(handoff, receivedHandoff)
        assertEquals("public-book", receivedCover)
        assertEquals("GB18030", receivedCharset)

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Deliver results").performClick()
        assertNull(receivedAnnotation)
        assertNull(receivedHandoff)
        assertNull(receivedCover)
        assertNull(receivedCharset)
    }
}
