/**
 * This class is generated by jOOQ
 */
package de.blizzy.backup.database.schema;

/**
 * This class is generated by jOOQ.
 *
 * A class modelling foreign key relationships between tables of the <code>PUBLIC</code> 
 * schema
 */
@java.lang.SuppressWarnings("all")
public class Keys {

	// IDENTITY definitions
	public static final org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.BackupsRecord, java.lang.Integer> IDENTITY_BACKUPS = Identities0.IDENTITY_BACKUPS;
	public static final org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> IDENTITY_ENTRIES = Identities0.IDENTITY_ENTRIES;
	public static final org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.FilesRecord, java.lang.Integer> IDENTITY_FILES = Identities0.IDENTITY_FILES;

	// UNIQUE and PRIMARY KEY definitions
	public static final org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.BackupsRecord> CONSTRAINT_1 = UniqueKeys0.CONSTRAINT_1;
	public static final org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.EntriesRecord> CONSTRAINT_C = UniqueKeys0.CONSTRAINT_C;
	public static final org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.FilesRecord> CONSTRAINT_3 = UniqueKeys0.CONSTRAINT_3;

	// FOREIGN KEY definitions

	/**
	 * No instances
	 */
	private Keys() {}

	@SuppressWarnings("hiding")
	private static class Identities0 extends org.jooq.impl.AbstractKeys {
		public static org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.BackupsRecord, java.lang.Integer> IDENTITY_BACKUPS = createIdentity(de.blizzy.backup.database.schema.tables.Backups.BACKUPS, de.blizzy.backup.database.schema.tables.Backups.BACKUPS.ID);
		public static org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> IDENTITY_ENTRIES = createIdentity(de.blizzy.backup.database.schema.tables.Entries.ENTRIES, de.blizzy.backup.database.schema.tables.Entries.ENTRIES.ID);
		public static org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.FilesRecord, java.lang.Integer> IDENTITY_FILES = createIdentity(de.blizzy.backup.database.schema.tables.Files.FILES, de.blizzy.backup.database.schema.tables.Files.FILES.ID);
	}

	@SuppressWarnings({"hiding", "unchecked"})
	private static class UniqueKeys0 extends org.jooq.impl.AbstractKeys {
		public static final org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.BackupsRecord> CONSTRAINT_1 = createUniqueKey(de.blizzy.backup.database.schema.tables.Backups.BACKUPS, de.blizzy.backup.database.schema.tables.Backups.BACKUPS.ID);
		public static final org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.EntriesRecord> CONSTRAINT_C = createUniqueKey(de.blizzy.backup.database.schema.tables.Entries.ENTRIES, de.blizzy.backup.database.schema.tables.Entries.ENTRIES.ID);
		public static final org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.FilesRecord> CONSTRAINT_3 = createUniqueKey(de.blizzy.backup.database.schema.tables.Files.FILES, de.blizzy.backup.database.schema.tables.Files.FILES.ID);
	}
}
