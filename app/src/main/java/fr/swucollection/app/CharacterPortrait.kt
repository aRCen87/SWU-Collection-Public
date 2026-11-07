package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/** Reviewed portraits bundled offline; the gallery and saved avatar use identical pixels. */
internal object CharacterPortrait {
    fun drawable(reference: String): Int = when (reference) {
        "SOR-005" -> R.drawable.avatar_sor_005
        "SOR-010" -> R.drawable.avatar_sor_010
        "SOR-009" -> R.drawable.avatar_sor_009
        "TWI-004" -> R.drawable.avatar_twi_004
        "SOR-017" -> R.drawable.avatar_sor_017
        "SOR-003" -> R.drawable.avatar_sor_003
        "TWI-011" -> R.drawable.avatar_twi_011
        "TWI-003" -> R.drawable.avatar_twi_003
        "SOR-236" -> R.drawable.avatar_sor_236
        "SOR-238" -> R.drawable.avatar_sor_238
        "SOR-015" -> R.drawable.avatar_sor_015
        "SHD-018" -> R.drawable.avatar_shd_018
        "TWI-009" -> R.drawable.avatar_twi_009
        "SOR-006" -> R.drawable.avatar_sor_006
        "SHD-004" -> R.drawable.avatar_shd_004
        "SOR-014" -> R.drawable.avatar_sor_014
        "TWI-012" -> R.drawable.avatar_twi_012
        "TWI-008" -> R.drawable.avatar_twi_008
        "TWI-007" -> R.drawable.avatar_twi_007
        "TWI-005" -> R.drawable.avatar_twi_005
        "TWI-015" -> R.drawable.avatar_twi_015
        "TWI-013" -> R.drawable.avatar_twi_013
        "TWI-014" -> R.drawable.avatar_twi_014
        "SHD-011" -> R.drawable.avatar_shd_011
        "SHD-017" -> R.drawable.avatar_shd_017
        "SHD-014" -> R.drawable.avatar_shd_014
        "SOR-016" -> R.drawable.avatar_sor_016
        "SOR-008" -> R.drawable.avatar_sor_008
        "LOF-015" -> R.drawable.avatar_lof_015
        "LOF-016" -> R.drawable.avatar_lof_016
        "LOF-017" -> R.drawable.avatar_lof_017
        "ASH-018" -> R.drawable.avatar_ash_018
        "ASH-013" -> R.drawable.avatar_ash_013
        "ASH-003" -> R.drawable.avatar_ash_003
        "SHD-012" -> R.drawable.avatar_shd_012
        "SOR-002" -> R.drawable.avatar_sor_002
        else -> error("Portrait indisponible")
    }
    fun encoded(context: Context, reference: String): String {
        val bitmap = BitmapFactory.decodeResource(context.resources, drawable(reference))
        return try {
            val bytes = java.io.ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out); out.toByteArray() }
            CommunityValidation.avatar("jpeg:" + Base64.encodeToString(bytes, Base64.NO_WRAP))
        } finally { bitmap.recycle() }
    }
}
