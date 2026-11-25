package com.postiepete;

import java.util.Set;
import net.runelite.api.gameval.NpcID;

/**
 * Moves a Phosani's Nightmare fight's {@link BossPhases#phosanisNightmare phases} on from the boss's form changes,
 * as seen in game. She wakes up from her initial form, which starts the fight. Breaking her shield turns her into
 * a weak form, which starts the pillar part. Charging the four pillars makes her summon sleepwalkers, and the first
 * to appear starts the sleepwalker part (she turns into her blast form a couple of ticks later, which is only a
 * fallback). Her next phase's form ends it. Her dying form ends the fight, which is where the game's
 * fight duration ends too (seen in game: wake-up to dying form was exactly the printed duration).
 *
 * Her first three phases come in a random order (seen in game: shield forms 4, 3, 2), so the parts are followed
 * in order rather than by the form's number. The final phase is the one after the third sleepwalker part.
 */
final class PhosaniNightmare
{
	// Her forms while the pillars are to be charged, after her shield is broken
	private static final Set<Integer> WEAK_FORMS = Set.of(
		NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_02,
		NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_03, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04);

	// Every form she is fought in, for hits on her
	private static final Set<Integer> FIGHT_FORMS = Set.of(
		NpcID.NIGHTMARE_CHALLENGE_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_PHASE_02, NpcID.NIGHTMARE_CHALLENGE_PHASE_03,
		NpcID.NIGHTMARE_CHALLENGE_PHASE_04, NpcID.NIGHTMARE_CHALLENGE_PHASE_05,
		NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_02,
		NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_03, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04,
		NpcID.NIGHTMARE_CHALLENGE_BLAST);

	// Each phase before the final one is her shielded part, the pillar part, then the sleepwalker part
	private static final int PARTS_PER_PHASE = 3;
	private static final int PILLAR = 1;
	private static final int SLEEPWALKER = 2;

	private PhosaniNightmare()
	{
	}

	/**
	 * Whether the NPC is Phosani's Nightmare in one of the forms she is fought in.
	 */
	static boolean isFightForm(int npcId)
	{
		return FIGHT_FORMS.contains(npcId);
	}

	/**
	 * Whether the NPC is one of the sleepwalkers she summons after the pillars are charged.
	 */
	static boolean isSleepwalker(int npcId)
	{
		return npcId == NpcID.NIGHTMARE_CHALLENGE_SLEEPWALKER;
	}

	/**
	 * Called when a sleepwalker appears. The first after the pillars are charged ends the pillar part and starts
	 * the sleepwalker part. The rest, and those of her final phase, change nothing.
	 */
	static void onSleepwalkerSpawned(BossPhases fight, int tick)
	{
		Integer part = fight.partIndex();
		if (part != null && part % PARTS_PER_PHASE == PILLAR)
		{
			fight.next(tick);
		}
	}

	/**
	 * Called when Phosani's Nightmare changes form, from oldId to newId.
	 */
	static void onFormChanged(BossPhases fight, int oldId, int newId, int tick)
	{
		if (oldId == NpcID.NIGHTMARE_CHALLENGE_INITIAL)
		{
			fight.start(tick);
			return;
		}

		Integer part = fight.partIndex();
		if (part == null)
		{
			return;
		}
		if (newId == NpcID.NIGHTMARE_CHALLENGE_DYING)
		{
			fight.finish(tick);
			return;
		}

		boolean partEnded;
		switch (part % PARTS_PER_PHASE)
		{
			case PILLAR:
				// The sleepwalkers appearing ends it first. This is for when they weren't seen.
				partEnded = WEAK_FORMS.contains(oldId);
				break;
			case SLEEPWALKER:
				partEnded = oldId == NpcID.NIGHTMARE_CHALLENGE_BLAST;
				break;
			default:
				partEnded = WEAK_FORMS.contains(newId);
		}
		if (partEnded)
		{
			fight.next(tick);
		}
	}
}
