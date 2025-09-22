package net.coljac.tiratnavandana.model

data class Chant(
    val id: String,
    val title: String,
    val audioFile: String,
    val verses: List<Verse>
)

data class Verse(
    val id: Int,
    val title: String,
    val startTime: Double,
    val endTime: Double,
    val text: String
)

