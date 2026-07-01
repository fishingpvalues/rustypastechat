JAVA_HOME ?= $(HOME)/jdk17
ANDROID_HOME ?= $(HOME)/android-sdk
GRADLE ?= $(HOME)/.local/bin/gradle
PROJECT_DIR ?= $(shell pwd)

export JAVA_HOME
export ANDROID_HOME
PATH := $(JAVA_HOME)/bin:$(GRADLE)/..:$(ANDROID_HOME)/platform-tools:$(PATH)

APK_DEBUG    = app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE  = app/build/outputs/apk/release/app-release-unsigned.apk
TEST_RESULTS = app/build/test-results

.PHONY: all debug release clean install uninstall lint test test-report

all: test debug

debug:
	$(GRADLE) assembleDebug --no-daemon

release:
	$(GRADLE) assembleRelease --no-daemon

clean:
	$(GRADLE) clean --no-daemon

install: debug
	adb install -r $(APK_DEBUG)

uninstall:
	adb uninstall com.rustypastechat

lint:
	$(GRADLE) lint --no-daemon

test:
	$(GRADLE) test --no-daemon

test-report:
	@find $(TEST_RESULTS) -name "TEST-*.xml" -exec echo "--- {} ---" \; -exec cat {} \;

deps:
	$(GRADLE) dependencies --no-daemon

help:
	@echo "RustyPaste Chat - Android App"
	@echo ""
	@echo "Targets:"
	@echo "  all          Run tests, then build debug APK (default)"
	@echo "  debug        Build debug APK"
	@echo "  release      Build release APK (unsigned)"
	@echo "  clean        Clean build artifacts"
	@echo "  install      Install debug APK on connected device via adb"
	@echo "  uninstall    Remove app from device"
	@echo "  test         Run all unit tests"
	@echo "  test-report  Print test results"
	@echo "  lint         Run lint checks"
	@echo "  deps         Show dependency tree"
