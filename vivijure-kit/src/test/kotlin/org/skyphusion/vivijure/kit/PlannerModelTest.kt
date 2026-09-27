package org.skyphusion.vivijure.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan and refine need a model: the host answers 400 without one (vivijure-android#24). */
class PlannerModelTest {
  @Test
  fun blankModelIsNotChosen() {
    assertNull(chosenPlannerModel(""))
  }

  @Test
  fun whitespaceOnlyModelIsNotChosen() {
    assertNull(chosenPlannerModel("  \n\t "))
  }

  @Test
  fun chosenModelIsReturned() {
    assertEquals("claude-sonnet-4-5", chosenPlannerModel("claude-sonnet-4-5"))
  }

  @Test
  fun chosenModelIsTrimmed() {
    assertEquals("@cf/meta/llama-3.1-8b-instruct", chosenPlannerModel("  @cf/meta/llama-3.1-8b-instruct \n"))
  }

  @Test
  fun refusalNamesTheMissingModel() {
    assertTrue(MISSING_PLANNER_MODEL_MESSAGE.lowercase().contains("model"))
  }
}
