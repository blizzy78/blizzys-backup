/**
 * This class is generated by jOOQ
 */
package de.blizzy.backup.database.schema;

/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings("nls")
public class Public extends org.jooq.impl.SchemaImpl {

	private static final long serialVersionUID = -1297225185;

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
	public final java.util.List<org.jooq.Sequence<?>> getSequences() {
		return java.util.Arrays.<org.jooq.Sequence<?>>asList(
			de.blizzy.backup.database.schema.Sequences.SYSTEM_SEQUENCE_272ECCB1_C60B_481D_A120_FE6BE013F157,
			de.blizzy.backup.database.schema.Sequences.SYSTEM_SEQUENCE_3DB333FB_C924_4A63_B127_4D4510874A0B,
			de.blizzy.backup.database.schema.Sequences.SYSTEM_SEQUENCE_4E57D207_CE3B_4692_8806_5237E4801233);
	}

	@Override
	public final java.util.List<org.jooq.Table<?>> getTables() {
		return java.util.Arrays.<org.jooq.Table<?>>asList(
			de.blizzy.backup.database.schema.tables.Backups.BACKUPS,
			de.blizzy.backup.database.schema.tables.Entries.ENTRIES,
			de.blizzy.backup.database.schema.tables.Files.FILES);
	}
}
