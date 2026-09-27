package org.skyphusion.vivijure.kit

/**
 * Shown when plan or refine is asked for with no model chosen. The host answers 400 without one,
 * and a default the user did not pick would bill a metered call to a model they never chose.
 */
const val MISSING_PLANNER_MODEL_MESSAGE = "Choose a model first. Plan and refine need one."

/** The model to send on plan or refine, or null when none is chosen (blank or whitespace only). */
fun chosenPlannerModel(raw: String): String? = raw.trim().ifEmpty { null }
