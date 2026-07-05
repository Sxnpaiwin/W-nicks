#!/bin/bash
# Repackage the final W-Nick JAR with patched classes and updated metadata.
set -e

ORIG_JAR="/home/z/my-project/upload/Name-Tag-Paper-1.0.81 (1).jar"
OUT_DIR=/home/z/my-project/build/out
PATCH_DIR=/home/z/my-project/build/patch
FINAL_JAR=/home/z/my-project/download/W-Nick-1.0.81.jar

mkdir -p /home/z/my-project/download
rm -f "$FINAL_JAR"

# Start from the original JAR (preserves all bundled libs like packetevents, commandapi, bookshelfapi, etc.)
cp "$ORIG_JAR" "$FINAL_JAR"

# 1) Replace patched .class files (only the ones we modified, leave bundled libs untouched)
cd "$OUT_DIR"
for cls in $(find gg/lode/nametag -name "*.class"); do
    /home/z/my-project/jdk21/bin/jar uf "$FINAL_JAR" "$cls"
done

# 2) Replace plugin.yml, paper-plugin.yml and config.yml with our patched versions
cd "$PATCH_DIR"
/home/z/my-project/jdk21/bin/jar uf "$FINAL_JAR" plugin.yml paper-plugin.yml config.yml

echo "=== Final JAR contents (W-Nick classes) ==="
unzip -l "$FINAL_JAR" | grep "gg/lode/nametag/" | grep -v packetevents
echo ""
echo "=== plugin.yml (head) ==="
unzip -p "$FINAL_JAR" plugin.yml | head -15
echo ""
echo "=== paper-plugin.yml ==="
unzip -p "$FINAL_JAR" paper-plugin.yml | head -20
echo ""
echo "=== Final JAR size ==="
ls -la "$FINAL_JAR"
