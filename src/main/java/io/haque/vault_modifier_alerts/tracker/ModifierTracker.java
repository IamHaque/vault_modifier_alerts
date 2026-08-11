package io.haque.vault_modifier_alerts.tracker;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ModifierTracker {

	private static final ModifierTracker INSTANCE = new ModifierTracker();

	private long generation;
	private long lastProcessedGeneration;
	private final Map<ResourceLocation, Integer> currentFrame = new HashMap<>();
	private final Map<ResourceLocation, Integer> lastSnapshot = new HashMap<>();
	private final Set<ResourceLocation> fired = new HashSet<>();
	private boolean inVault;
	private long suppressUntilTick;

	private ModifierTracker() {
	}

	public static ModifierTracker getInstance() {
		return INSTANCE;
	}

	public void recordFrameEntry(ResourceLocation id, Integer timeLeft) {
		if (currentFrame.isEmpty()) {
			generation++;
		}
		currentFrame.put(id, timeLeft);
	}

	public boolean hasUnprocessedFrame() {
		return generation != lastProcessedGeneration;
	}

	public long getGeneration() {
		return generation;
	}

	public Map<ResourceLocation, Integer> consumeFrame() {
		Map<ResourceLocation, Integer> snapshot = new HashMap<>(currentFrame);
		currentFrame.clear();
		lastProcessedGeneration = generation;
		return snapshot;
	}

	public void resetSession(boolean nowInVault, int graceTicks, int playerTickCount) {
		lastSnapshot.clear();
		currentFrame.clear();
		fired.clear();
		generation = lastProcessedGeneration;
		inVault = nowInVault;
		suppressUntilTick = nowInVault ? playerTickCount + graceTicks : 0L;
	}

	public boolean isInVault() {
		return inVault;
	}

	public long getSuppressUntilTick() {
		return suppressUntilTick;
	}

	public Map<ResourceLocation, Integer> getLastSnapshot() {
		return lastSnapshot;
	}

	public void setLastSnapshot(Map<ResourceLocation, Integer> snapshot) {
		lastSnapshot.clear();
		lastSnapshot.putAll(snapshot);
	}

	public boolean isFired(ResourceLocation id) {
		return fired.contains(id);
	}

	public void markFired(ResourceLocation id) {
		fired.add(id);
	}

	public void reArm(ResourceLocation id) {
		fired.remove(id);
	}
}