package io.cloudcauldron.bocan.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
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
class UserVisibleStringConcatenation(config: Config = Config.empty) :
    Rule(
        config,
        description = "User-visible sentences must be whole string resources with placeholders, never assembled with plus."
    ) {
    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (expression.operationToken != KtTokens.PLUS) return
        val resourceOperand = listOfNotNull(expression.left, expression.right).any(::resolvesStringResource)
        if (resourceOperand) {
            report(
                Finding(
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
