package io.cloudcauldron.bocan.lint

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags `+` expressions where an operand resolves a string resource
 * (stringResource, pluralStringResource, getString): building user-visible
 * sentences by concatenation bakes English word order into code and breaks
 * under translation and pseudolocale. Put the whole sentence in the resource
 * with placeholders instead.
 */
class UserVisibleStringConcatenation(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Maintainability,
        description = "User-visible sentences must be whole string resources with placeholders, never assembled with plus.",
        debt = Debt.TEN_MINS
    )

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (expression.operationToken != KtTokens.PLUS) return
        val resourceOperand = listOfNotNull(expression.left, expression.right).any(::resolvesStringResource)
        if (resourceOperand) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "A string resource is concatenated with plus; move the whole sentence into the resource with placeholders."
                )
            )
        }
    }

    private fun resolvesStringResource(operand: KtExpression): Boolean {
        val calls = operand.collectDescendantsOfType<KtCallExpression>().toMutableList()
        (operand as? KtCallExpression)?.let(calls::add)
        return calls.any { it.calleeExpression?.text in RESOURCE_CALLS }
    }

    private companion object {
        val RESOURCE_CALLS = setOf("stringResource", "pluralStringResource", "getString", "getQuantityString")
    }
}
