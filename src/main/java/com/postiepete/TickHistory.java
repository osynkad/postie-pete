package com.postiepete;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A value over time, by game tick, so a kill can be sent with the value as it was when its fight started: the
 * player's items, or a raid's party size. A snapshot is only added when the value changed.
 */
class TickHistory<T>
{
	/**
	 * How long snapshots are kept: 3 hours, longer than an Inferno or Chambers of Xeric run.
	 */
	static final int MAX_AGE_TICKS = 18000;

	private final Deque<Snapshot<T>> snapshots = new ArrayDeque<>();

	/**
	 * Adds the value as it is on this tick, unless it is the same as the last snapshot.
	 */
	void record(int tick, T value)
	{
		Snapshot<T> last = snapshots.peekLast();
		if (last != null && last.value.equals(value))
		{
			return;
		}
		if (last != null && last.tick == tick)
		{
			snapshots.pollLast();
		}
		snapshots.addLast(new Snapshot<>(tick, value));
	}

	/**
	 * The value as it was on the tick: the newest snapshot taken on it or before. Null if the tick is before
	 * the oldest snapshot, e.g. before logging in or turning the plugin on.
	 */
	T at(int tick)
	{
		Iterator<Snapshot<T>> newestFirst = snapshots.descendingIterator();
		while (newestFirst.hasNext())
		{
			Snapshot<T> snapshot = newestFirst.next();
			if (snapshot.tick <= tick)
			{
				return snapshot.value;
			}
		}
		return null;
	}

	/**
	 * Drops snapshots older than {@link #MAX_AGE_TICKS}, but keeps the one still in effect at that point.
	 */
	void prune(int tick)
	{
		int cutoff = tick - MAX_AGE_TICKS;
		while (snapshots.size() >= 2)
		{
			Snapshot<T> oldest = snapshots.pollFirst();
			if (snapshots.peekFirst().tick > cutoff)
			{
				snapshots.addFirst(oldest);
				return;
			}
		}
	}

	void reset()
	{
		snapshots.clear();
	}

	private static class Snapshot<T>
	{
		final int tick;
		final T value;

		Snapshot(int tick, T value)
		{
			this.tick = tick;
			this.value = value;
		}
	}
}
