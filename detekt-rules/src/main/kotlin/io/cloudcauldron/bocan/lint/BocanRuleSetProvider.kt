package io.cloudcauldron.bocan.lint

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** The repo's own rules: user-facing strings stay in resources, whole and untangled. */
class BocanRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "bocan"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            UserVisibleStringConcatenation(config),
            BareTextLiteral(config)
        )
    )
}
