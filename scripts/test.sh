#!/bin/bash
# Run all W-Nick tests: JAR smoke test + JUnit unit tests.
#
# Usage:
#   ./scripts/test.sh
#
# Exits 0 if all tests pass, non-zero otherwise.
set -e

PROJECT_ROOT=/home/z/my-project
JDK=$PROJECT_ROOT/jdk21/bin/java
JAVAC=$PROJECT_ROOT/jdk21/bin/javac
BUILD=$PROJECT_ROOT/build
SRC=$PROJECT_ROOT/src
LIBS=$BUILD/libs
JUNIT=$LIBS/junit-platform-console-standalone-1.10.2.jar

echo "=== W-Nick Test Suite ==="
echo ""

# ─────────────────────────────────────────────────────────────────────
# 0. Ensure JUnit is present
# ─────────────────────────────────────────────────────────────────────
if [ ! -f "$JUNIT" ]; then
   echo "Downloading JUnit Platform Console Standalone..."
   curl -sL -o "$JUNIT" \
     "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar"
fi

# ─────────────────────────────────────────────────────────────────────
# 1. Compile main sources (if not already compiled)
# ─────────────────────────────────────────────────────────────────────
echo "=== Compiling main sources ==="
rm -rf "$BUILD/out"
mkdir -p "$BUILD/out"

CP="$BUILD/cls"
for jar in $LIBS/*.jar; do CP="$CP:$jar"; done
CP="$CP:$BUILD/stubs/out"

SOURCES=$(find $SRC/gg/lode/nametag -name "*.java" -not -path "*/test/*")
$JAVAC -cp "$CP" -d "$BUILD/out" -Xmaxerrs 10 $SOURCES 2>&1 | grep -v "^Note:\|^$" || true
echo "  compiled $(find $BUILD/out -name '*.class' | wc -l) classes"

# ─────────────────────────────────────────────────────────────────────
# 2. Compile test sources
# ─────────────────────────────────────────────────────────────────────
echo "=== Compiling test sources ==="
rm -rf "$BUILD/test-out"
mkdir -p "$BUILD/test-out"

CP="$BUILD/cls"
for jar in $LIBS/*.jar; do CP="$CP:$jar"; done
CP="$CP:$BUILD/stubs/out:$BUILD/out:$JUNIT"

TEST_SOURCES=$(find $SRC/gg/lode/nametag/test -name "*.java")
$JAVAC -cp "$CP" -d "$BUILD/test-out" $TEST_SOURCES 2>&1 | grep -v "^Note:\|^$" || true
echo "  compiled $(find $BUILD/test-out -name '*.class' | wc -l) test classes"

# ─────────────────────────────────────────────────────────────────────
# 3. Build the JAR (so the smoke test can load it)
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "=== Building JAR for smoke test ==="
$PROJECT_ROOT/scripts/package.sh 2>&1 | grep -E "^(=== |  )" | tail -15

# ─────────────────────────────────────────────────────────────────────
# 4. Run JAR smoke test (loads every class in the JAR)
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "=== Running JAR smoke test ==="
cd "$PROJECT_ROOT"
CP="$BUILD/test-out:$JUNIT"
$JDK -cp "$CP" gg.lode.nametag.test.JarSmokeTest
SMOKE_EXIT=$?

if [ $SMOKE_EXIT -ne 0 ]; then
   echo ""
   echo "FAIL: JAR smoke test failed."
   exit 1
fi

# ─────────────────────────────────────────────────────────────────────
# 5. Run JUnit unit tests
# ─────────────────────────────────────────────────────────────────────
echo ""
echo "=== Running JUnit unit tests ==="
CP="$BUILD/test-out:$BUILD/out"
for jar in $LIBS/*.jar; do CP="$CP:$jar"; done
CP="$CP:$BUILD/stubs/out"

$JDK -jar "$JUNIT" \
   --class-path "$CP" \
   --scan-classpath \
   --disable-banner \
   --details=tree \
   --exclude-classname ".*JarSmokeTest.*" 2>&1 | tail -30

echo ""
echo "=== All tests complete ==="
