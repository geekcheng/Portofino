/*
 * Copyright (C) 2005-2010 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * There are special exceptions to the terms and conditions of the GPL
 * as it is applied to this software. View the full text of the
 * exception in file OPEN-SOURCE-LICENSE.txt in the directory of this
 * software distribution.
 *
 * This program is distributed WITHOUT ANY WARRANTY; and without the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/gpl.txt
 * or write to:
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307  USA
 *
 */

package com.manydesigns.portofino.model;

import com.manydesigns.elements.logging.LogUtil;
import com.manydesigns.portofino.model.io.ModelParser;
import junit.framework.TestCase;

import java.util.List;
import java.util.logging.Level;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
*/
public class DBParserTest extends TestCase {
    public static final String copyright =
            "Copyright (c) 2005-2010, ManyDesigns srl";

    ModelParser parser;
    Model model;

    public void setUp() {
        LogUtil.initializeLoggingSystem();
        ModelParser.logger.setLevel(Level.ALL);

        parser = new ModelParser();
    }

    public void testParseJpetStorePostgresql() {
        try {
            model = parser.parse(
                    "databases/jpetstore/postgresql/jpetstore-postgres.xml");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        assertNotNull(model);

        List<Database> databases = model.getDatabases();
        assertEquals(2, databases.size());

        Database database = databases.get(0);
        assertEquals("jpetstore", database.getDatabaseName());

        List<Schema> schemas = database.getSchemas();
        assertEquals(1, schemas.size());

        Schema schema = schemas.get(0);
        assertEquals("jpetstore", schema.getDatabaseName());
        assertEquals("public", schema.getSchemaName());
        assertEquals("jpetstore.public", schema.getQualifiedName());

        List<Table> tables = schema.getTables();
        assertEquals(3, tables.size());

        // tabella 0
        Table table0 = tables.get(1);
        checkTable(table0, "jpetstore", "public", "category");

        List<Column> columns0 = table0.getColumns();
        assertEquals(3, columns0.size());

        checkColumn(columns0.get(0),
                "jpetstore", "public", "category", "catid",
                "varchar", false, 10, 0);
        checkColumn(columns0.get(1),
                "jpetstore", "public", "category", "name",
                "varchar", true, 80, 0);
        checkColumn(columns0.get(2),
                "jpetstore", "public", "category", "descn",
                "varchar", true, 255, 0);

        PrimaryKey primaryKey0 = table0.getPrimaryKey();
        assertEquals("pk_category", primaryKey0.getPkName());
        List<Column> pkColumns0 = primaryKey0.getColumns();
        assertEquals(1, pkColumns0.size());
        assertEquals(columns0.get(0), pkColumns0.get(0));
        assertEquals(1, table0.getOneToManyRelationships().size());
        checkRelationships(table0.getOneToManyRelationships()
                , 0, "fk_product_1", "public" ,
                "category", "NO ACTION", "NO ACTION");
        List<Reference> references =
                table0.getOneToManyRelationships().get(0).getReferences();
        checkReference(references, 0, "category", "catid");


        // tabella 1
        Table table1 = tables.get(2);
        checkTable(table1, "jpetstore", "public", "product");

        int idxRel = 0;
        checkRelationships(table1.getManyToOneRelationships()
                , idxRel, "fk_product_1", "public" ,
                "category", "NO ACTION", "NO ACTION");
        assertEquals(1, table1.getManyToOneRelationships().size());
        List<Reference> references2 =
                table1.getManyToOneRelationships().get(idxRel).getReferences();
        checkReference(references2, 0, "category", "catid");

        List<Column> columns1 = table1.getColumns();
        assertEquals(4, columns1.size());

        checkColumn(columns1.get(0),
                "jpetstore", "public", "product", "productid",
                "varchar", false, 10, 0);
        checkColumn(columns1.get(1),
                "jpetstore", "public", "product", "category",
                "varchar", false, 10, 0);
        checkColumn(columns1.get(2),
                "jpetstore", "public", "product", "name",
                "varchar", true, 80, 0);
        checkColumn(columns1.get(3),
                "jpetstore", "public", "product", "descn",
                "varchar", true, 255, 0);

        // tabella 2
        Table table2 = tables.get(0);
        checkTable(table2, "jpetstore", "public", "lineitem");

        List<Column> columns2 = table2.getColumns();
        assertEquals(3, columns0.size());

        checkColumn(columns2.get(0),
                "jpetstore", "public", "lineitem", "orderid",
                "int4", false, 8, 0);
        checkColumn(columns2.get(1),
                "jpetstore", "public", "lineitem", "linenum",
                "int4", false, 8, 0);
        checkColumn(columns2.get(2),
                "jpetstore", "public", "lineitem", "itemid",
                "varchar", false, 255, 0);
        checkColumn(columns2.get(3),
                "jpetstore", "public", "lineitem", "quantity",
                "int4", false, 8, 0);
        checkColumn(columns2.get(4),
                "jpetstore", "public", "lineitem", "unitprice",
                "numeric", false, 10, 2);

        PrimaryKey primaryKey2 = table2.getPrimaryKey();
        assertEquals("pk_lineitem", primaryKey2.getPkName());
        List<Column> pkColumns2 = primaryKey2.getColumns();
        assertEquals(2, pkColumns2.size());
        assertEquals(columns2.get(0), pkColumns2.get(0));
        assertEquals(columns2.get(1), pkColumns2.get(1));
    }

    private void checkReference(List<Reference> references, int idx,
                                String fromColumn, String toColumn) {
        Reference ref = references.get(0);
        assertEquals(fromColumn, ref.fromColumn.getColumnName());
        assertEquals(toColumn, ref.toColumn.getColumnName());
    }

    private void checkRelationships(List<Relationship> relationships, int idx, String name,
                                    String toSchema, String toTable,
                                    String onUpdate, String onDelete) {
        Relationship rel = relationships.get(idx);
        assertEquals(name, rel.getRelationshipName());
        assertEquals(toSchema, rel.getToTable().getSchemaName());
        assertEquals(toTable, rel.getToTable().getTableName());
        assertEquals(onUpdate, rel.getOnUpdate());
        assertEquals(onUpdate, rel.getOnDelete());

    }

    public void testFindTableByQualifiedName() {
        try {
            model = parser.parse(
                    "databases/jpetstore/postgresql/jpetstore-postgres.xml");
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private void checkColumn(Column column, String databaseName,
                             String schemaName, String tableName,
                             String columnName, String columnType,
                             boolean nullable, int length, int scale) {
        assertEquals(databaseName, column.getDatabaseName());
        assertEquals(schemaName, column.getSchemaName());
        assertEquals(tableName, column.getTableName());
        assertEquals(columnName, column.getColumnName());
        assertEquals(columnType.toUpperCase(), column.getColumnType().toUpperCase());
        assertEquals(nullable, column.isNullable());
        assertEquals(length, column.getLength());
        assertEquals(scale, column.getScale());
        assertEquals(databaseName + "." + schemaName + "." +
                tableName + "." + columnName, column.getQualifiedName());
    }

    private void checkTable(Table table, String databaseName,
                            String schemaName, String tableName) {
        assertEquals(databaseName, table.getDatabaseName());
        assertEquals(schemaName, table.getSchemaName());
        assertEquals(tableName, table.getTableName());
        assertEquals(databaseName + "." + schemaName + "." +
                tableName, table.getQualifiedName());
    }
}