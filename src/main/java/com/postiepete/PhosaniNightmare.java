package com.postiepete;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.NpcID;

/**
 * Moves a Phosani's Nightmare fight's {@link BossPhases#phosanisNightmare phases} on from the boss's and the pillars'
 * form changes, as seen in game. She wakes up from her initial form, which starts the fight. Breaking her shield
 * turns her into a weak form, which starts the pillar part. The last of the four pillars turning charged starts the
 * sleepwalker part, when the game says "All four totems are fully charged." (seen in game: she turns into her blast
 * form 6 ticks later, and the sleepwalkers appear 2 ticks after that; her blast form is only a fallback). Her next
 * phase's form ends it. Her dying form ends the fight, which is where the game's fight duration ends too (seen in
 * game: wake-up to dying form was exactly the printed duration).
 *
 * Her first three phases come in a random order (seen in game: shield forms 4, 3, 2), so the parts are followed
 * in order rather than by the form's number. The final phase is the one after the third sleepwalker part.
 */
class PhosaniNightmare
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

	// Each pillar's charged form. Each of the four has its own forms, so they can be told apart by id.
	private static final Set<Integer> CHARGED_PILLARS = Set.of(
		NpcID.NIGHTMARE_TOTEM_1_CHARGED, NpcID.NIGHTMARE_TOTEM_2_CHARGED,
		NpcID.NIGHTMARE_TOTEM_3_CHARGED, NpcID.NIGHTMARE_TOTEM_4_CHARGED);

	private static final Set<Integer> PILLARS = Set.of(
		NpcID.NIGHTMARE_TOTEM_1_DORMANT, NpcID.NIGHTMARE_TOTEM_1_READY, NpcID.NIGHTMARE_TOTEM_1_CHARGED,
		NpcID.NIGHTMARE_TOTEM_2_DORMANT, NpcID.NIGHTMARE_TOTEM_2_READY, NpcID.NIGHTMARE_TOTEM_2_CHARGED,
		NpcID.NIGHTMARE_TOTEM_3_DORMANT, NpcID.NIGHTMARE_TOTEM_3_READY, NpcID.NIGHTMARE_TOTEM_3_CHARGED,
		NpcID.NIGHTMARE_TOTEM_4_DORMANT, NpcID.NIGHTMARE_TOTEM_4_READY, NpcID.NIGHTMARE_TOTEM_4_CHARGED);

	// Each phase before the final one is her shielded part, the pillar part, then the sleepwalker part
	private static final int PARTS_PER_PHASE = 3;
	private static final int PILLAR = 1;
	private static final int SLEEPWALKER = 2;

	private final BossPhases fight;

	// The pillars charged right now, by their charged form
	private final Set<Integer> chargedPillars = new HashSet<>();

	PhosaniNightmare(BossPhases fight)
	{
		this.fight = fight;
	}

	/**
	 * Whether the NPC is Phosani's Nightmare in one of the forms she is fought in.
	 */
	static boolean isFightForm(int npcId)
	{
		return FIGHT_FORMS.contains(npcId);
	}

	/**
	 * Whether the NPC is one of the four pillars, in any form.
	 */
	static boolean isPillar(int npcId)
	{
		return PILLARS.contains(npcId);
	}

	/**
	 * Called when a pillar changes form, from oldId to newId. The last of the four to turn charged ends the
	 * pillar part and starts the sleepwalker part.
	 */
	void onPillarChanged(int oldId, int newId, int tick)
	{
		chargedPillars.remove(oldId);
		if (!CHARGED_PILLARS.contains(newId))
		{
			return;
		}
		chargedPillars.add(newId);

		Integer part = fight.partIndex();
		if (chargedPillars.size() == CHARGED_PILLARS.size() && part != null && part % PARTS_PER_PHASE == PILLAR)
		{
			fight.next(tick);
		}
	}

	/**
	 * Called when Phosani's Nightmare changes form, from oldId to newId.
	 */
	void onFormChanged(int oldId, int newId, int tick)
	{
		if (oldId == NpcID.NIGHTMARE_CHALLENGE_INITIAL)
		{
			chargedPillars.clear();
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
				// The pillars being charged ends it first. This is for when that wasn't seen.
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

	/**
	 * Forgets the pillars, e.g. on logging out.
	 */
	void reset()
	{
		chargedPillars.clear();
	}
}
