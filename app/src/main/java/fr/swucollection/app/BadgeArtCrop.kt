package fr.swucollection.app

/** Illustration coordinates in the original card, before any badge sizing or slicing. */
internal data class BadgeArtRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    /** Center-crop only INSIDE the illustration: no destination shape can expose card text. */
    fun fit(aspect: Float): BadgeArtRect {
        require(aspect > 0f && aspect.isFinite())
        return if (width / height > aspect) {
            val inset = (width - height * aspect) / 2f
            copy(left = left + inset, right = right - inset)
        } else {
            val inset = (height - width / aspect) / 2f
            copy(top = top + inset, bottom = bottom - inset)
        }
    }
}

internal object BadgeArtCrop {
    // Showcase leaders place their rules over the art, unlike standard leaders.
    // These are the three showcase faces used by the badge catalogue.
    private val specialFaces = mapOf(
        "C24-006" to BadgeArtRect(.15f, .12f, .91f, .64f),
        "SHD-265" to BadgeArtRect(.33f, .19f, .83f, .75f),
        "SHD-266" to BadgeArtRect(.31f, .20f, .78f, .75f),
        "SHD-276" to BadgeArtRect(.08f, .20f, .57f, .75f)
    )

    fun illustration(reference: String, type: String, width: Float, height: Float): BadgeArtRect {
        require(width > 0f && height > 0f && width.isFinite() && height.isFinite())
        val normalized = specialFaces[reference] ?: when {
            width > height && type.equals("Base", ignoreCase = true) -> BadgeArtRect(.09f, .19f, .91f, .81f)
            width > height -> BadgeArtRect(.08f, .11f, .43f, .79f)
            type.equals("Amélioration", ignoreCase = true) || type.equals("Upgrade", ignoreCase = true) ->
                BadgeArtRect(.15f, .13f, .91f, .53f)
            else -> BadgeArtRect(.15f, .17f, .91f, .59f)
        }
        return BadgeArtRect(normalized.left * width, normalized.top * height, normalized.right * width, normalized.bottom * height)
    }
}
