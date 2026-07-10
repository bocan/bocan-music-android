#!/bin/sh
# Installs the repo's git hooks. Run once after cloning:
#   ./scripts/install-hooks.sh

set -eu

repo_root=$(git rev-parse --show-toplevel)
hooks_dir="$repo_root/.git/hooks"

mkdir -p "$hooks_dir"

cat > "$hooks_dir/pre-commit" <<'HOOK'
#!/bin/sh
# Pre-commit: run ktlint and detekt when staged Kotlin files exist.
set -eu

# A JAVA_HOME pointing at a removed JDK would sink every gradlew run;
# fall back to whatever java is on the PATH instead.
if [ -n "${JAVA_HOME:-}" ] && [ ! -x "$JAVA_HOME/bin/java" ]; then
    unset JAVA_HOME
fi

staged_kotlin=$(git diff --cached --name-only --diff-filter=ACMR | grep -E '\.(kt|kts)$' || true)

if [ -z "$staged_kotlin" ]; then
    exit 0
fi

echo "pre-commit: staged Kotlin files found, running ktlint and detekt"
./gradlew --quiet ktlintCheck detekt

echo "pre-commit: lint gates passed"
HOOK

chmod +x "$hooks_dir/pre-commit"
echo "Installed pre-commit hook to $hooks_dir/pre-commit"
