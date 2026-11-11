# Bòcan Music for Android: developer entry points.
#
# `make help` lists everything. `make ci` runs exactly what GitHub Actions runs.
#
# Guard rails: if JAVA_HOME points at a JDK that no longer exists (a common
# leftover in shell profiles), fall back to the Homebrew JDK 25 or the PATH
# java so gradlew never dies on a stale variable. Gradle then runs its daemon
# on JDK 25 regardless (gradle/gradle-daemon-jvm.properties).

BREW_JDK := /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home

ifeq ($(wildcard $(JAVA_HOME)/bin/java),)
  ifneq ($(wildcard $(BREW_JDK)/bin/java),)
    export JAVA_HOME := $(BREW_JDK)
  else
    unexport JAVA_HOME
  endif
endif

GRADLE   := ./gradlew
SDK_DIR  := $(shell sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)
ADB      := $(SDK_DIR)/platform-tools/adb
EMULATOR := $(SDK_DIR)/emulator/emulator

APP_ID   := io.cloudcauldron.bocan.android
ACTIVITY := io.cloudcauldron.bocan.app.MainActivity
AVD      ?= bocan-api36
APK      := app/build/outputs/apk/debug/app-debug.apk

# `make test m=:core:sync` scopes tests to one module.
m ?=

.DEFAULT_GOAL := help

# ---------------------------------------------------------------- everyday --

.PHONY: build
build: ## Assemble the debug APK
	$(GRADLE) assembleDebug

.PHONY: test
test: ## Run unit tests (all modules, or one with m=:core:sync)
ifeq ($(m),)
	$(GRADLE) test
else
	$(GRADLE) $(m):test
endif

.PHONY: lint
lint: tag-audit ## ktlint + detekt + Android Lint, plus the source tag audit
	$(GRADLE) check

.PHONY: tag-audit
tag-audit: ## Every interactive call site carries an E2E test tag (no device; docs/e2e.md)
	python3 -m unittest discover -s scripts/e2e -p 'test_*.py'
	python3 scripts/e2e/audit-test-tags.py --strict

.PHONY: format
format: ## Auto-fix formatting with ktlint
	$(GRADLE) ktlintFormat

.PHONY: coverage
coverage: ## Generate and open the Kover HTML coverage report
	$(GRADLE) koverHtmlReport
	open core/observability/build/reports/kover/html/index.html

.PHONY: ci
ci: tag-audit ## Run exactly what CI runs: tag audit, check test koverVerify assembleDebug
	$(GRADLE) check test koverVerify assembleDebug

.PHONY: clean
clean: ## Delete all build outputs
	$(GRADLE) clean

# ------------------------------------------------------------------ device --

.PHONY: install
install: ## Build and install the debug APK on the connected device
	$(GRADLE) installDebug

.PHONY: run
run: install ## Install and launch the app
	$(ADB) shell am start -n $(APP_ID)/$(ACTIVITY)

.PHONY: stop
stop: ## Force-stop the app on the device
	$(ADB) shell am force-stop $(APP_ID)

.PHONY: log
log: ## Tail logcat for the running app only
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s $(APP_ID))

.PHONY: screenshot
screenshot: ## Capture the device screen to screenshots/<timestamp>.png
	mkdir -p screenshots
	$(ADB) exec-out screencap -p > screenshots/$$(date +%Y%m%d-%H%M%S).png
	ls -t screenshots | head -1

.PHONY: devices
devices: ## List connected devices and emulators
	$(ADB) devices

# ---------------------------------------------------------------- emulator --

.PHONY: emulator
emulator: ## Boot the AVD (default bocan-api36) with a window
	$(EMULATOR) -avd $(AVD) -no-boot-anim -no-audio &

.PHONY: emulator-headless
emulator-headless: ## Boot the AVD without a window (CI-style)
	$(EMULATOR) -avd $(AVD) -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &

.PHONY: emulator-kill
emulator-kill: ## Shut down the running emulator
	$(ADB) emu kill

.PHONY: emulator-wait
emulator-wait: ## Wait until the attached emulator has finished booting
	scripts/e2e/wait-for-boot.sh

# ------------------------------------------------------------- end-to-end --

.PHONY: end-to-end
end-to-end: ## Full E2E suite on the attached device, else a headless bocan-api36 (docs/e2e.md)
	E2E_AVD=$(AVD) scripts/e2e/run.sh E2ESuite

.PHONY: end-to-end-smoke
end-to-end-smoke: ## The E2E smoke subset (one fixture journey)
	E2E_AVD=$(AVD) scripts/e2e/run.sh SmokeSuite

.PHONY: end-to-end-audit
end-to-end-audit: ## Only the on-device tag audits (every control on every screen has a tag)
	E2E_AVD=$(AVD) scripts/e2e/run.sh AuditSuite

.PHONY: end-to-end-report
end-to-end-report: ## Open the last E2E HTML report
	open build/e2e/report/index.html

# ----------------------------------------------------------------- repo ----

.PHONY: hooks
hooks: ## Install the pre-commit hook (ktlint + detekt on staged Kotlin)
	./scripts/install-hooks.sh

.PHONY: doctor
doctor: ## Show toolchain status: JDK, SDK, Gradle, devices
	@echo "JAVA_HOME: $${JAVA_HOME:-(unset, using PATH java)}"
	@java -version 2>&1 | head -1
	@echo "sdk.dir:   $(if $(SDK_DIR),$(SDK_DIR),(missing local.properties))"
	@$(GRADLE) --version | grep -E "^Gradle" || true
	@$(ADB) devices 2>/dev/null || echo "adb: not found under $(SDK_DIR)"

.PHONY: apk
apk: ## Print the debug APK path (builds it if missing)
	@test -f $(APK) || $(GRADLE) --quiet assembleDebug
	@echo $(APK)

.PHONY: help
help: ## List available targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
