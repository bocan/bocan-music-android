package io.cloudcauldron.bocan.lint

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** The repo's own rules: user-facing strings stay in resources, whole and untangled. */
class BocanRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("bocan")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::UserVisibleStringConcatenation,
            ::BareTextLiteral
        )
    )
}
