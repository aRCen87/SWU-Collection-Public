package fr.swucollection.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Bundled publisher logos also work offline; supplementary sets retain their exact code. */
internal fun setLogoResource(code: String): Int {
    val normalized = code.uppercase(Locale.ROOT)
    return when (normalized) {
        "SOR", "PSOR", "PSORJ", "ESOR", "SOROP", "SS1", "SS1J", "SS2", "SS2J" -> R.drawable.set_logo_sor
        "SHD", "SHDOP", "SHDPQ", "SHDPQJ", "PSHD", "PSHDJ", "ESHD" -> R.drawable.set_logo_shd
        "TWI", "TWIOP", "TWIPQ", "TWIPQJ", "PTWI", "PTWIJ", "TWISS", "TWISSJ" -> R.drawable.set_logo_twi
        "JTL", "JTLP", "JTLOP" -> R.drawable.set_logo_jtl
        "LOF", "LOFP", "LOFOP" -> R.drawable.set_logo_lof
        "SEC", "SECP", "SECOP" -> R.drawable.set_logo_sec
        "LAW", "LAWP", "LAWOP" -> R.drawable.set_logo_law
        "ASH", "ASHP", "ASHOP" -> R.drawable.set_logo_ash
        "HMW", "HMWP", "HMWOP" -> R.drawable.set_logo_hmw
        "IC27" -> R.drawable.set_logo_ic27
        "IBH" -> R.drawable.set_logo_ibh
        else -> R.drawable.set_logo_swu
    }
}

@Composable
internal fun SetLogo(code: String, modifier: Modifier = Modifier) {
    Image(painterResource(setLogoResource(code)), "Logo de l’extension $code",
        modifier.size(112.dp, 64.dp), contentScale = ContentScale.Fit)
}
