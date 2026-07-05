#!/bin/bash
# Repackage the final W-Nick JAR with patched classes, updated metadata,
# and dead-weight stripped for a smaller download.
set -e

ORIG_JAR="/home/z/my-project/upload/Name-Tag-Paper-1.0.81 (1).jar"
OUT_DIR=/home/z/my-project/build/out
PATCH_DIR=/home/z/my-project/build/patch
FINAL_JAR=/home/z/my-project/download/W-Nick-1.0.81.jar
WORK_DIR=/home/z/my-project/build/jar-work

mkdir -p /home/z/my-project/download
rm -f "$FINAL_JAR"

# Start by extracting the original JAR to a working directory so we can
# strip dead weight before re-zipping.
echo "=== Extracting original JAR ==="
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"
unzip -q "$ORIG_JAR"

# ─────────────────────────────────────────────────────────────────────
# Strip dead weight to make the JAR lightweight.
# Total stripped: ~11 MB uncompressed → ~5-6 MB compressed savings.
# ─────────────────────────────────────────────────────────────────────

echo "=== Stripping dead weight ==="

# 1. Bundled Adventure API (net/kyori/) — Paper already provides this at
#    runtime. We compiled against Paper-API's Adventure, so removing the
#    bundled duplicate is safe. Saves ~1.5 MB uncompressed.
rm -rf net/kyori
rm -rf META-INF/maven/net/kyori
echo "  stripped net/kyori/ (Adventure API — Paper provides at runtime)"

# 2. MongoDB driver (com/mongodb + org/bson + org/slf4j) — only needed
#    if the user picks MONGODB storage. Default is LOCAL. The
#    StorageManager already handles the missing driver gracefully via
#    reflection (falls back to LOCAL with a clear error). Users who want
#    MongoDB can install the driver as a separate server library.
#    Saves ~5.4 MB uncompressed.
rm -rf com/mongodb
rm -rf org/bson
rm -rf org/slf4j
rm -rf META-INF/maven/org.mongodb
rm -rf META-INF/maven/org.slf4j
echo "  stripped com/mongodb + org/bson + org/slf4j (MongoDB driver — not needed for LOCAL storage)"

# 3. Apache HttpClient (org/apache/hc/) — was used by the removed
#    CloudNickService (which uploaded data to lode.gg). Since we deleted
#    CloudNickService, this is completely dead weight.
#    Also strip mozilla/public-suffix-list.txt (used by Apache HttpClient
#    for cookie domain validation). Saves ~4.4 MB uncompressed.
rm -rf org/apache
rm -rf META-INF/maven/org.apache.httpcomponents
rm -f mozilla/public-suffix-list.txt
rmdir mozilla 2>/dev/null || true
echo "  stripped org/apache/hc/ + mozilla/public-suffix-list.txt (Apache HttpClient — was for removed CloudNickService)"

# 4. net/infumia/titleupdater/ — bundled title-update library that nobody
#    in W-Nick references. Saves ~82 KB uncompressed.
rm -rf net/infumia
rm -rf META-INF/maven/net.infumia
echo "  stripped net/infumia/titleupdater/ (unused title library)"

# 5. PacketEvents bStats — NOTE: we do NOT strip these! Even though W-Nick
#    no longer calls Metrics(this, 24781), the PacketEvents
#    SpigotPacketEventsBuilder$1 class references bstats/bukkit/Metrics,
#    bstats/charts/CustomChart, and bstats/charts/SimplePie in its method
#    signatures. The JVM verifies these on class load, so stripping them
#    causes NoClassDefFoundError at onLoad() time. The entire bStats
#    package is only ~51 KB compressed — not worth the risk.
# (Previously stripped; restored after crash report from user.)
echo "  kept io/github/retrooper/packetevents/nametag/bstats/ (PacketEvents requires it)"

# 6. GeyserUtil — we don't support GeyserMC, this is dead weight.
rm -f io/github/retrooper/packetevents/nametag/util/GeyserUtil.class
echo "  stripped GeyserUtil.class (no GeyserMC support)"

# 7. Old CommandAPI NMS classes — keep only the ones compatible with
#    Paper 1.21.8+ (the minimum api-version we declare).
#    Keep: NMS_1_21_R7 (1.21.8), NMS_26_1 (1.21.11), NMS_Common.
#    Strip: NMS_1_20_R4, NMS_1_21_R1 through R6. Saves ~500 KB.
for f in dev/jorel/commandapi/nametag/nms/NMS_1_20_R4*.class \
         dev/jorel/commandapi/nametag/nms/NMS_1_21_R1*.class \
         dev/jorel/commandapi/nametag/nms/NMS_1_21_R2*.class \
         dev/jorel/commandapi/nametag/nms/NMS_1_21_R3*.class \
         dev/jorel/commandapi/nametag/nms/NMS_1_21_R4*.class \
         dev/jorel/commandapi/nametag/nms/NMS_1_21_R5*.class \
         dev/jorel/commandapi/nametag/nms/NMS_1_21_R6*.class; do
   rm -f "$f"
done
echo "  stripped old CommandAPI NMS classes (keep R7, 26_1, Common only)"

# 8. Old PacketEvents version mappings — keep only 1.21+ mappings.
#    Strip V_1_13 through V_1_20_5. Saves ~1 MB uncompressed.
for v in V_1_13 V_1_13_2 V_1_14 V_1_15 V_1_16 V_1_16_2 V_1_17 \
         V_1_19 V_1_19_3 V_1_19_4 \
         V_1_20 V_1_20_2 V_1_20_3 V_1_20_5; do
   rm -f "assets/mappings/data/block_state/${v}.nbt"
done
echo "  stripped old PacketEvents block-state mappings (keep 1.21+ only)"

# ─────────────────────────────────────────────────────────────────────
# Replace patched .class files (our W-Nick code)
# ─────────────────────────────────────────────────────────────────────

echo "=== Replacing patched classes ==="
cd "$OUT_DIR"
for cls in $(find gg/lode/nametag -name "*.class"); do
   cp "$cls" "$WORK_DIR/$cls"
done

# ─────────────────────────────────────────────────────────────────────
# Replace plugin.yml, paper-plugin.yml, config.yml
# ─────────────────────────────────────────────────────────────────────

echo "=== Updating metadata ==="
cp "$PATCH_DIR/plugin.yml"        "$WORK_DIR/plugin.yml"
cp "$PATCH_DIR/paper-plugin.yml"  "$WORK_DIR/paper-plugin.yml"
cp "$PATCH_DIR/config.yml"        "$WORK_DIR/config.yml"

# ─────────────────────────────────────────────────────────────────────
# Repackage with maximum compression
# ─────────────────────────────────────────────────────────────────────

echo "=== Repackaging ==="
cd "$WORK_DIR"
rm -f "$FINAL_JAR"
zip -9 -q -r "$FINAL_JAR" . -x "*.git/*"
echo "  repackaged with max compression (zip -9)"

echo ""
echo "=== Final JAR ==="
ls -la "$FINAL_JAR"
echo ""
echo "=== Size comparison ==="
ORIG_SIZE=$(stat -c%s "$ORIG_JAR")
FINAL_SIZE=$(stat -c%s "$FINAL_JAR")
SAVED=$((ORIG_SIZE - FINAL_SIZE))
PCT=$((SAVED * 100 / ORIG_SIZE))
echo "Original: $ORIG_SIZE bytes ($((ORIG_SIZE / 1024 / 1024)) MB $((ORIG_SIZE / 1024 % 1024)) KB)"
echo "Final:    $FINAL_SIZE bytes ($((FINAL_SIZE / 1024 / 1024)) MB $((FINAL_SIZE / 1024 % 1024)) KB)"
echo "Saved:    $SAVED bytes ($PCT%)"
echo ""
echo "=== Verify plugin.yml ==="
unzip -p "$FINAL_JAR" plugin.yml | head -8
echo ""
echo "=== Verify key classes present ==="
unzip -l "$FINAL_JAR" | grep -E "(plugin\.yml|paper-plugin\.yml|WNickGuideCommand|TabIntegration|NameTagPlugin|FakeRankManager)\.class"
echo ""
echo "=== Verify stripped packages are gone ==="
for pkg in net/kyori com/mongodb org/apache org/bson org/slf4j net/infumia; do
   count=$(unzip -l "$FINAL_JAR" | grep -c "$pkg/")
   echo "  $pkg: $count files (should be 0)"
done
