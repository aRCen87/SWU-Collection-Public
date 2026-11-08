package fr.swucollection.app
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Disposable fixture calls the production composer; no account, Firebase or real message is used. */
class MessengerKeyboardTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 @Test fun sendRemainsAboveTheRealKeyboardAndKeepsItOpenAfterSending(){
  ui.activity.enableEdgeToEdge()
  ui.setContent {
   MaterialTheme {
    var draft by remember{mutableStateOf("")};var messages by remember{mutableStateOf(listOf(PlayerMessage("first","friend","text","Bonjour","","",1L)))}
    Column(Modifier.fillMaxSize().testTag("fixture_root").safeDrawingPadding().imePadding().padding(16.dp)) {
     MessengerChatLayout("me","Ami test",messages,draft,{draft=it},false,true,null,MessengerSignal(),null,emptyList(),
      {messages=messages+PlayerMessage("sent","me","text",draft,"","",2L);draft=""},{},{},{},{},{})
    }
   }
  }
  ui.onNodeWithTag("messenger_draft").performClick().performTextInput("Essai clavier")
  ui.waitUntil(8000){ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}
  val density=ui.activity.resources.displayMetrics.density
  val keyboard=ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
  assertTrue("Real keyboard is not open",keyboard>0)
  val bottom=(ui.activity.window.decorView.height-keyboard)/density
  val send=ui.onNodeWithTag("messenger_send").getUnclippedBoundsInRoot()
  assertTrue("Send is hidden below keyboard",send.bottom.value<=bottom+2)
  ui.onNodeWithTag("messenger_send").assertIsEnabled().performTouchInput{click()}
  ui.onNodeWithText("Essai clavier").assertIsDisplayed()
  ui.onNodeWithTag("messenger_draft").assertIsFocused()
  assertTrue(ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime()))
  val file=File(ui.activity.getExternalFilesDir(null),"home-test-messenger-keyboard.png")
  file.outputStream().use{ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 }
}
