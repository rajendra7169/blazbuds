#!/usr/bin/env bash
# What can be checked of the iPhone app on Linux, where there is no Xcode, before spending a cloud build on it.
#
# 1. The Kotlin for iPhone (core and ui, iosMain included) is compiled for real against Apple's own API
#    descriptions, which Kotlin ships for every computer. Only linking it into a framework needs the Mac.
# 2. Every Swift file is parsed.
# 3. The Swift is type-checked against hand-made stand-ins for UIKit, SwiftUI, CoreBluetooth, ExternalAccessory and
#    the Kotlin framework (iosApp/checks/stubs). They carry Apple's signatures for what the app uses, so a wrong
#    label or type, or a call between the app's own files that doesn't fit, shows up here. They can't prove Apple's
#    side: optional delegate methods, for one, aren't checked. Only Xcode on the cloud Mac is the final word.
# 4. The finder's matching rules (Routes.swift) run against a small fake CoreBluetooth.
# 5. The names the Swift side calls on the Kotlin framework are checked against the Kotlin sources.
#
# The Swift toolchain (once, ~2.8 GB, no root needed):
#   curl -LO https://download.swift.org/swift-6.1-release/ubuntu2404/swift-6.1-RELEASE/swift-6.1-RELEASE-ubuntu24.04.tar.gz
#   mkdir -p ~/.local/share && tar xzf swift-6.1-RELEASE-ubuntu24.04.tar.gz -C ~/.local/share
#   mv ~/.local/share/swift-6.1-RELEASE-ubuntu24.04 ~/.local/share/swift-6.1
#
# Usage: scripts/check-ios.sh [--swift-only]
set -u
cd "$(dirname "$0")/.."
status=0

if [ "${1:-}" != "--swift-only" ]; then
    echo "Compiling the Kotlin for iPhone (no linking)…"
    if nice ./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64 --console=plain -q; then
        echo "  ok"
    else
        echo "  ^ Kotlin errors above."
        status=1
    fi
    echo
fi

SWIFTC="${SWIFTC:-$HOME/.local/share/swift-6.1/usr/bin/swiftc}"
[ -x "$SWIFTC" ] || SWIFTC="$(command -v swiftc || true)"
if [ -z "$SWIFTC" ] || [ ! -x "$SWIFTC" ]; then
    echo "No Swift toolchain found; see the install lines at the top of this script."
    exit 127
fi

SOURCES=(iosApp/Sources/*.swift)

echo "Parsing every Swift file…"
if "$SWIFTC" -parse "${SOURCES[@]}"; then echo "  ok"; else status=1; fi

echo
echo "Type-checking the Swift against the stand-ins…"
MODS="$(mktemp -d)"
trap 'rm -rf "$MODS"' EXIT
built=1
for module in UIKit SwiftUI CoreBluetooth ExternalAccessory BlazbudsKit; do
    if ! "$SWIFTC" -swift-version 5 -parse-as-library -emit-module -module-name "$module" -I "$MODS" \
        "iosApp/checks/stubs/$module.swift" -emit-module-path "$MODS/$module.swiftmodule" 2>&1; then
        echo "  the $module stand-in doesn't build"
        built=0
    fi
done
if [ $built -eq 1 ] && "$SWIFTC" -swift-version 5 -typecheck -I "$MODS" "${SOURCES[@]}"; then
    echo "  ok"
else
    status=1
fi

echo
echo "Running the finder's matching rules…"
sed 's/^import CoreBluetooth//' iosApp/Sources/Routes.swift > "$MODS/Routes.swift"
if "$SWIFTC" -module-name RoutesTest iosApp/checks/routes-test/FakeCoreBluetooth.swift "$MODS/Routes.swift" \
    iosApp/checks/routes-test/main.swift -o "$MODS/routes-test" && "$MODS/routes-test" | tee "$MODS/routes.txt" | grep -v "^ok" ; then :; fi
if [ -s "$MODS/routes.txt" ] && ! grep -q "^FAIL" "$MODS/routes.txt"; then echo "  ok ($(grep -c "^ok" "$MODS/routes.txt") rules)"; else status=1; fi

echo
echo "Checking the Kotlin names Swift calls…"
if python3 - <<'EOF'
import re, sys, pathlib
kotlin = "".join(pathlib.Path(p).read_text() for p in [
    "ui/src/iosMain/kotlin/com/blazify/buds/ui/Bridge.kt",
    "ui/src/iosMain/kotlin/com/blazify/buds/ui/MainViewController.kt",
])
stub = pathlib.Path("iosApp/checks/stubs/BlazbudsKit.swift").read_text()
problems = []
for name in sorted(set(re.findall(r"^\s*(?:override\s+)?fun\s+([A-Za-z]\w*)\s*\(", kotlin, re.M))):
    if re.match(r"(new|copy|alloc|init|mutableCopy)([A-Z]|$)", name):
        problems.append(f"{name}: Swift sees it as do{name[0].upper()}{name[1:]}; rename it")
for name in re.findall(r"func\s+([A-Za-z]\w*)\s*\(", stub):
    if not re.search(rf"\bfun\s+{name}\s*\(", kotlin):
        problems.append(f"{name}: in the stand-in but not in the Kotlin")
for kind, name in re.findall(r"^(interface|class|object)\s+(\w+)", kotlin, re.M):
    if not re.search(rf"\b(protocol|class)\s+{name}\b", stub):
        problems.append(f"{name}: a Kotlin {kind} the stand-in lacks")
print("\n".join("  " + p for p in problems) if problems else "  ok")
sys.exit(1 if problems else 0)
EOF
then :; else status=1; fi

echo
echo "Checking for Swift types declared twice…"
DUPLICATES=$(cat "${SOURCES[@]}" | grep -oE '^(public |final |private |fileprivate )*(struct|class|enum|actor|protocol) [A-Za-z_][A-Za-z0-9_]*' | awk '{print $NF}' | sort | uniq -d)
if [ -z "$DUPLICATES" ]; then echo "  ok"; else echo "$DUPLICATES" | sed 's/^/  declared twice: /'; status=1; fi

echo
if [ $status -eq 0 ]; then
    echo "Clean. (Not a promise it builds: only Xcode on the cloud Mac checks Swift against the real Apple frameworks.)"
else
    echo "Errors above. These would fail the cloud build."
fi
exit $status
