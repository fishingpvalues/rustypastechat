UNAME_S := $(shell uname -s)

ifeq ($(UNAME_S),Darwin)
JAVA_HOME ?= /opt/homebrew/opt/openjdk@21
ANDROID_HOME ?= /opt/homebrew/share/android-commandlinetools
GRADLE ?= /opt/homebrew/bin/gradle
else
JAVA_HOME ?= $(HOME)/jdk17
ANDROID_HOME ?= $(HOME)/android-sdk
GRADLE ?= $(HOME)/.local/bin/gradle
endif
PROJECT_DIR ?= $(shell pwd)

AVD_NAME ?= MelanoScan_API35
RUSTYPASTE_BIN ?= $(HOME)/.cargo/bin/rustypaste
RUSTYPASTE_PORT ?= 8000
RUSTYPASTE_DIR ?= /tmp/rustypaste-test

export JAVA_HOME
export ANDROID_HOME
PATH := $(JAVA_HOME)/bin:$(dir $(GRADLE)):$(ANDROID_HOME)/platform-tools:$(ANDROID_HOME)/emulator:$(PATH)

APK_DEBUG    = app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE  = app/build/outputs/apk/release/app-release-unsigned.apk
TEST_RESULTS = app/build/test-results

.PHONY: all ci debug release clean install uninstall lint test test-report deps \
        emulator-start emulator-wait emulator-stop logcat rustypaste-start rustypaste-stop e2e

all: test debug

ci: lint test debug

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

emulator-start:
	@$(ANDROID_HOME)/emulator/emulator -avd $(AVD_NAME) -no-boot-anim -netdelay none -netspeed full > /tmp/emulator.log 2>&1 &
	@echo "Emulator $(AVD_NAME) starting in background, log: /tmp/emulator.log"

emulator-wait:
	adb wait-for-device
	@until [ "$$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
	@echo "Emulator booted."

emulator-stop:
	-adb -s emulator-5554 emu kill

logcat:
	adb logcat -v time | grep -i --line-buffered rustypastechat

rustypaste-start:
	@mkdir -p $(RUSTYPASTE_DIR)/upload
	@echo 'server.address = "0.0.0.0:$(RUSTYPASTE_PORT)"' > $(RUSTYPASTE_DIR)/config.toml
	@echo 'server.max_content_length = "10MB"' >> $(RUSTYPASTE_DIR)/config.toml
	@echo 'server.upload_path = "$(RUSTYPASTE_DIR)/upload"' >> $(RUSTYPASTE_DIR)/config.toml
	@echo 'server.timeout = "30s"' >> $(RUSTYPASTE_DIR)/config.toml
	@echo 'server.expose_version = true' >> $(RUSTYPASTE_DIR)/config.toml
	@echo 'server.expose_list = true' >> $(RUSTYPASTE_DIR)/config.toml
	@echo '' >> $(RUSTYPASTE_DIR)/config.toml
	@echo '[paste]' >> $(RUSTYPASTE_DIR)/config.toml
	@echo 'default_extension = "txt"' >> $(RUSTYPASTE_DIR)/config.toml
	@echo 'duplicate_files = true' >> $(RUSTYPASTE_DIR)/config.toml
	CONFIG=$(RUSTYPASTE_DIR)/config.toml $(RUSTYPASTE_BIN) > $(RUSTYPASTE_DIR)/server.log 2>&1 &
	@echo "rustypaste starting on :$(RUSTYPASTE_PORT), log: $(RUSTYPASTE_DIR)/server.log"

rustypaste-stop:
	-pkill -f "$(RUSTYPASTE_BIN)"

e2e: rustypaste-start emulator-start emulator-wait install
	@echo "Stack up: rustypaste on :$(RUSTYPASTE_PORT), app installed on $(AVD_NAME)."

help:
	@echo "RustyPaste Chat - Android App"
	@echo ""
	@echo "Targets:"
	@echo "  all          Run tests, then build debug APK (default)"
	@echo "  ci           Full CI pipeline: lint + test + build debug APK"
	@echo "  debug        Build debug APK"
	@echo "  release      Build release APK (unsigned)"
	@echo "  clean        Clean build artifacts"
	@echo "  install      Install debug APK on connected device via adb"
	@echo "  uninstall    Remove app from device"
	@echo "  test         Run all unit tests"
	@echo "  test-report  Print test results"
	@echo "  lint         Run lint checks"
	@echo "  deps         Show dependency tree"
	@echo "  emulator-start  Boot AVD_NAME in background"
	@echo "  emulator-wait   Block until emulator finishes booting"
	@echo "  emulator-stop   Kill running emulator"
	@echo "  logcat          Tail app logcat, filtered"
	@echo "  rustypaste-start  Run local rustypaste server on RUSTYPASTE_PORT"
	@echo "  rustypaste-stop   Kill local rustypaste server"
	@echo "  e2e             Bring up rustypaste + emulator + install app"
