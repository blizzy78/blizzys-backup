/**
 * This class is generated by jOOQ
 */
package de.blizzy.backup.database.schema.tables;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(value    = "http://jooq.sourceforge.net",
                            comments = "This class is generated by jOOQ")
@SuppressWarnings("nls")
public class Entries extends org.jooq.impl.UpdatableTableImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord> {

	private static final long serialVersionUID = 1246130460;

	/**
	 * The singleton instance of ENTRIES
	 */
	public static final de.blizzy.backup.database.schema.tables.Entries ENTRIES = new de.blizzy.backup.database.schema.tables.Entries();

	/**
	 * The class holding records for this type
	 */
	private static final java.lang.Class<de.blizzy.backup.database.schema.tables.records.EntriesRecord> __RECORD_TYPE = de.blizzy.backup.database.schema.tables.records.EntriesRecord.class;

	/**
	 * The class holding records for this type
	 */
	@Override
	public java.lang.Class<de.blizzy.backup.database.schema.tables.records.EntriesRecord> getRecordType() {
		return __RECORD_TYPE;
	}

	/**
	 * An uncommented item
	 * 
	 * PRIMARY KEY
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> ID = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer>("ID", org.jooq.impl.SQLDataType.INTEGER, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> PARENT_ID = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer>("PARENT_ID", org.jooq.impl.SQLDataType.INTEGER, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> BACKUP_ID = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer>("BACKUP_ID", org.jooq.impl.SQLDataType.INTEGER, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Byte> TYPE = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Byte>("TYPE", org.jooq.impl.SQLDataType.TINYINT, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.sql.Timestamp> CREATION_TIME = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.sql.Timestamp>("CREATION_TIME", org.jooq.impl.SQLDataType.TIMESTAMP, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.sql.Timestamp> MODIFICATION_TIME = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.sql.Timestamp>("MODIFICATION_TIME", org.jooq.impl.SQLDataType.TIMESTAMP, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Boolean> HIDDEN = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Boolean>("HIDDEN", org.jooq.impl.SQLDataType.BOOLEAN, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.String> NAME = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.String>("NAME", org.jooq.impl.SQLDataType.VARCHAR, ENTRIES);

	/**
	 * An uncommented item
	 */
	public static final org.jooq.TableField<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> FILE_ID = new org.jooq.impl.TableFieldImpl<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer>("FILE_ID", org.jooq.impl.SQLDataType.INTEGER, ENTRIES);

	/**
	 * No further instances allowed
	 */
	private Entries() {
		super("ENTRIES", de.blizzy.backup.database.schema.Public.PUBLIC);
	}

	@Override
	public org.jooq.Identity<de.blizzy.backup.database.schema.tables.records.EntriesRecord, java.lang.Integer> getIdentity() {
		return de.blizzy.backup.database.schema.Keys.IDENTITY_ENTRIES;
	}

	@Override
	public org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.EntriesRecord> getMainKey() {
		return de.blizzy.backup.database.schema.Keys.CONSTRAINT_C;
	}

	@Override
	@SuppressWarnings("unchecked")
	public java.util.List<org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.EntriesRecord>> getKeys() {
		return java.util.Arrays.<org.jooq.UniqueKey<de.blizzy.backup.database.schema.tables.records.EntriesRecord>>asList(de.blizzy.backup.database.schema.Keys.CONSTRAINT_C);
	}
}