/**
 * This class is generated by jOOQ
 */
package de.blizzy.backup.database.schema.tables.records;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(value    = "http://jooq.sourceforge.net",
                            comments = "This class is generated by jOOQ")
public class BackupsRecord extends org.jooq.impl.UpdatableRecordImpl<de.blizzy.backup.database.schema.tables.records.BackupsRecord> {

	private static final long serialVersionUID = -533374619;

	/**
	 * An uncommented item
	 * 
	 * PRIMARY KEY
	 */
	public void setId(java.lang.Integer value) {
		setValue(de.blizzy.backup.database.schema.tables.Backups.ID, value);
	}

	/**
	 * An uncommented item
	 * 
	 * PRIMARY KEY
	 */
	public java.lang.Integer getId() {
		return getValue(de.blizzy.backup.database.schema.tables.Backups.ID);
	}

	/**
	 * An uncommented item
	 */
	public void setRunTime(java.sql.Timestamp value) {
		setValue(de.blizzy.backup.database.schema.tables.Backups.RUN_TIME, value);
	}

	/**
	 * An uncommented item
	 */
	public java.sql.Timestamp getRunTime() {
		return getValue(de.blizzy.backup.database.schema.tables.Backups.RUN_TIME);
	}

	/**
	 * An uncommented item
	 */
	public void setNumEntries(java.lang.Integer value) {
		setValue(de.blizzy.backup.database.schema.tables.Backups.NUM_ENTRIES, value);
	}

	/**
	 * An uncommented item
	 */
	public java.lang.Integer getNumEntries() {
		return getValue(de.blizzy.backup.database.schema.tables.Backups.NUM_ENTRIES);
	}

	/**
	 * Create a detached BackupsRecord
	 */
	public BackupsRecord() {
		super(de.blizzy.backup.database.schema.tables.Backups.BACKUPS);
	}

	/**
	 * Create an attached BackupsRecord
	 */
	public BackupsRecord(org.jooq.Configuration configuration) {
		super(de.blizzy.backup.database.schema.tables.Backups.BACKUPS, configuration);
	}
}
