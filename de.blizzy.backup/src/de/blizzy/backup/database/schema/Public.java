/**
 * This class is generated by jOOQ
 */
package de.blizzy.backup.database.schema;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(value    = "http://jooq.sourceforge.net",
                            comments = "This class is generated by jOOQ")
@SuppressWarnings("nls")
public class Public extends org.jooq.impl.SchemaImpl {

	private static final long serialVersionUID = 1768066194;

	/**
	 * The singleton instance of PUBLIC
	 */
	public static final Public PUBLIC = new Public();

	/**
	 * No further instances allowed
	 */
	private Public() {
		super("PUBLIC");
	}

	@Override
	public final java.util.List<org.jooq.Sequence> getSequences() {
		return java.util.Arrays.<org.jooq.Sequence>asList(
			de.blizzy.backup.database.schema.Sequences.SYSTEM_SEQUENCE_7793367E_B01B_413F_9E09_47F40E6347B8,
			de.blizzy.backup.database.schema.Sequences.SYSTEM_SEQUENCE_83357AAB_ECD6_4989_A03B_C5985DA4BFAB,
			de.blizzy.backup.database.schema.Sequences.SYSTEM_SEQUENCE_E9EB531E_3071_444D_9DE6_DAE7B1F0E917);
	}

	@Override
	public final java.util.List<org.jooq.Table<?>> getTables() {
		return java.util.Arrays.<org.jooq.Table<?>>asList(
			de.blizzy.backup.database.schema.tables.Backups.BACKUPS,
			de.blizzy.backup.database.schema.tables.Files.FILES,
			de.blizzy.backup.database.schema.tables.Entries.ENTRIES);
	}
}
