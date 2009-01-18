package eu.medsea.util;

import java.util.Iterator;

public class MatchingMagicMimeEntry
{
	public MatchingMagicMimeEntry(MagicMimeEntry magicMimeEntry) // , boolean exactMatch)
	{
		this.magicMimeEntry = magicMimeEntry;
//		this.exactMatch = exactMatch;
	}

	private MagicMimeEntry magicMimeEntry;
//	private boolean exactMatch;
//	private int level = -1;
	private double specificity = -1; // can only be positive when initialised - at least with our current formula ;-)

	public MagicMimeEntry getMagicMimeEntry() {
		return magicMimeEntry;
	}
//	public boolean isExactMatch() {
//		return exactMatch;
//	}
//	public int getLevel() {
//		if (level < 0) {
//			int l = 0;
//			MagicMimeEntry parent = magicMimeEntry.getParent();
//			while (parent != null) {
//				++l;
//				parent = parent.getParent();
//			}
//			level = l;
//		}
//
//		return level;
//	}

	private int getLevel() {
		int l = 0;
		MagicMimeEntry parent = magicMimeEntry.getParent();
		while (parent != null) {
			++l;
			parent = parent.getParent();
		}
		return l;
	}

	private int getRecursiveSubEntryCount()
	{
		return getRecursiveSubEntryCount(magicMimeEntry, 0);
	}

	public int getRecursiveSubEntryCount(MagicMimeEntry entry, int subLevel)
	{
		++subLevel;
		int result = 0;
		for (Iterator it = entry.getSubEntries().iterator(); it.hasNext();) {
			MagicMimeEntry subEntry = (MagicMimeEntry) it.next();
			result += subLevel * (1 + getRecursiveSubEntryCount(subEntry, subLevel));
		}
		return result;
	}

	public double getSpecificity() {
		if (specificity < 0) {
			// The higher the level, the more specific it probably is.
			// The more children below the current match, the less specific it is.
			// TODO This formula need to be changed/optimized.
			specificity = (double)(getLevel() + 1) / (getRecursiveSubEntryCount() + 1);
		}

		return specificity;
	}

	public String getMimeType()
	{
		return magicMimeEntry.getMimeType();
	}

	public String toString() {
		return this.getClass().getName() + '[' + getMimeType() + ',' + getSpecificity() + ']';
	}
}
