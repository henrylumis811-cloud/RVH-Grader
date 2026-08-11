package com.henrylumis.rvhgrader.model

/** One band of the mark->aggregate table, e.g. "90 and above -> aggregate 1". */
data class AggregateBand(val minMark: Int, val aggregate: Int)

/** One band of the aggregate-sum->division table, e.g. "up to 12 -> Division I". */
data class DivisionBand(val maxAggSum: Int, val label: String)

/**
 * Editable version of the scoring rules that used to be hardcoded. A teacher can adjust either
 * table from Settings; anything they don't touch keeps the original UNEB-style defaults this
 * app shipped with.
 */
data class GradingScale(
    val aggregateBands: List<AggregateBand>,
    val divisionBands: List<DivisionBand>
) {
    fun aggregateFor(score: Int): Int {
        val sorted = aggregateBands.sortedByDescending { it.minMark }
        return sorted.firstOrNull { score >= it.minMark }?.aggregate
            ?: sorted.lastOrNull()?.aggregate
            ?: 9
    }

    fun divisionFor(aggSum: Int, grossSum: Int): String {
        if (grossSum == 0) return "U"
        val sorted = divisionBands.sortedBy { it.maxAggSum }
        return sorted.firstOrNull { aggSum <= it.maxAggSum }?.label ?: "U"
    }

    companion object {
        fun default(): GradingScale = GradingScale(
            aggregateBands = listOf(
                AggregateBand(90, 1),
                AggregateBand(80, 2),
                AggregateBand(70, 3),
                AggregateBand(60, 4),
                AggregateBand(50, 5),
                AggregateBand(45, 6),
                AggregateBand(40, 7),
                AggregateBand(35, 8),
                AggregateBand(0, 9)
            ),
            divisionBands = listOf(
                DivisionBand(12, "I"),
                DivisionBand(24, "II"),
                DivisionBand(28, "III"),
                DivisionBand(32, "IV")
            )
        )
    }
}
