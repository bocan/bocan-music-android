package io.cloudcauldron.bocan.lint

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Flags a string literal or template passed as the text of a `Text(...)`
 * composable: user-facing copy lives in strings.xml, never inline (the
 * no_bare_user_facing_literal rule shared with the Mac repo).
 */
class BareTextLiteral(config: Config = Config.empty) :
    Rule(
        config,
        description = "Text() must render a string resource, not an inline literal or template."
    ) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != "Text") return
        val textArgument = expression.valueArguments.firstOrNull { argument ->
            val name = argument.getArgumentName()?.asName?.asString()
            name == "text" || (name == null && argument == expression.valueArguments.firstOrNull())
        } ?: return
        if (textArgument.getArgumentExpression() is KtStringTemplateExpression) {
            report(
                Finding(
                    Entity.from(expression),
                    "Inline text in a Text() composable; move the copy to strings.xml and use stringResource."
                )
            )
        }
    }
}
