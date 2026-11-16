package com.wowstudio.claudephotosorter

object CategoryRules {
    private val rules = linkedMapOf(
        "People" to setOf("person", "people", "face", "human", "man", "woman", "child", "boy", "girl"),
        "Animals" to setOf("animal", "dog", "cat", "bird", "horse", "fish", "wildlife", "pet"),
        "Food" to setOf("food", "meal", "dish", "fruit", "vegetable", "pizza", "dessert", "drink"),
        "Nature" to setOf("nature", "plant", "flower", "tree", "forest", "mountain", "sky", "sunset", "beach"),
        "Buildings" to setOf("building", "architecture", "house", "home", "tower", "church", "bridge", "city"),
        "Vehicles" to setOf("vehicle", "car", "truck", "bus", "motorcycle", "bicycle", "train", "airplane", "boat"),
        "Documents" to setOf("document", "text", "paper", "receipt", "book", "letter", "form", "poster")
    )
    fun classify(labels: List<String>): String {
        val normalized = labels.map { it.lowercase() }.toSet()
        if (normalized.any { it.contains("screenshot") || it.contains("screen shot") }) return "Screenshots"
        return rules.entries.firstOrNull { (_, terms) ->
            normalized.any { label -> terms.any { term -> label == term || label.contains(term) } }
        }?.key ?: "Other"
    }
}
