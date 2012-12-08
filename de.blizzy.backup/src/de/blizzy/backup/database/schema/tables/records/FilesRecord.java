/**
 * This class is generated by jOOQ
 */
package de.blizzy.backup.database.schema.tables.records;

/**
 * This class is generated by jOOQ.
 */
@java.lang.SuppressWarnings("all")
public class FilesRecord extends org.jooq.impl.UpdatableRecordImpl<de.blizzy.backup.database.schema.tables.records.FilesRecord> {

	private static final long serialVersionUID = -1754214466;

	/**
	 * The table column <code>PUBLIC.FILES.ID</code>
	 * <p>
	 * This column is part of the table's PRIMARY KEY
	 */
	public void setId(java.lang.Integer value) {
		setValue(de.blizzy.backup.database.schema.tables.Files.FILES.ID, value);
	}

	/**
	 * The table column <code>PUBLIC.FILES.ID</code>
	 * <p>
	 * This column is part of the table's PRIMARY KEY
	 */
	public java.lang.Integer getId() {
		return getValue(de.blizzy.backup.database.schema.tables.Files.FILES.ID);
	}

	/**
	 * The table column <code>PUBLIC.FILES.BACKUP_PATH</code>
	 */
	public void setBackupPath(java.lang.String value) {
		setValue(de.blizzy.backup.database.schema.tables.Files.FILES.BACKUP_PATH, value);
	}

	/**
	 * The table column <code>PUBLIC.FILES.BACKUP_PATH</code>
	 */
	public java.lang.String getBackupPath() {
		return getValue(de.blizzy.backup.database.schema.tables.Files.FILES.BACKUP_PATH);
	}

	/**
	 * The table column <code>PUBLIC.FILES.CHECKSUM</code>
	 */
	public void setChecksum(java.lang.String value) {
		setValue(de.blizzy.backup.database.schema.tables.Files.FILES.CHECKSUM, value);
	}

	/**
	 * The table column <code>PUBLIC.FILES.CHECKSUM</code>
	 */
	public java.lang.String getChecksum() {
		return getValue(de.blizzy.backup.database.schema.tables.Files.FILES.CHECKSUM);
	}

	/**
	 * The table column <code>PUBLIC.FILES.LENGTH</code>
	 */
	public void setLength(java.lang.Long value) {
		setValue(de.blizzy.backup.database.schema.tables.Files.FILES.LENGTH, value);
	}

	/**
	 * The table column <code>PUBLIC.FILES.LENGTH</code>
	 */
	public java.lang.Long getLength() {
		return getValue(de.blizzy.backup.database.schema.tables.Files.FILES.LENGTH);
	}

	/**
	 * The table column <code>PUBLIC.FILES.COMPRESSION</code>
	 */
	public void setCompression(java.lang.Byte value) {
		setValue(de.blizzy.backup.database.schema.tables.Files.FILES.COMPRESSION, value);
	}

	/**
	 * The table column <code>PUBLIC.FILES.COMPRESSION</code>
	 */
	public java.lang.Byte getCompression() {
		return getValue(de.blizzy.backup.database.schema.tables.Files.FILES.COMPRESSION);
	}

	/**
	 * Create a detached FilesRecord
	 */
	public FilesRecord() {
		super(de.blizzy.backup.database.schema.tables.Files.FILES);
	}
}
