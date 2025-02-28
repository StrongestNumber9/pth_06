/*
 * Teragrep Archive Datasource (pth_06)
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.pth_06.planner.walker.conditions;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;

import java.util.Objects;

import static org.jooq.impl.SQLDataType.BIGINTUNSIGNED;

/**
 * Row condition that compares the compareTo tables bloom filter bytes against category table
 */
public final class CategoryTableCondition implements QueryCondition {

    private final Table<?> comparedTo;
    private final Condition bloomTermCondition;
    private final Condition typeIdCondition;
    private final Table<Record> categoryTable;

    public CategoryTableCondition(Table<?> comparedTo, long bloomTermId) {
        this(
                comparedTo,
                DSL.field("term_id", BIGINTUNSIGNED.nullable(false)).eq(ULong.valueOf(bloomTermId)),
                DSL.field("type_id", BIGINTUNSIGNED.nullable(false)).eq((Field<ULong>) comparedTo.field("filter_type_id")), DSL.table(DSL.name(("term_" + bloomTermId + "_" + comparedTo.getName())))
        );
    }

    public CategoryTableCondition(
            Table<?> comparedTo,
            Condition bloomTermCondition,
            Condition typeIdCondition,
            Table<Record> categoryTable
    ) {
        this.comparedTo = comparedTo;
        this.bloomTermCondition = bloomTermCondition;
        this.typeIdCondition = typeIdCondition;
        this.categoryTable = categoryTable;
    }

    public Condition condition() {
        final Field<byte[]> filterField = DSL.field(DSL.name(categoryTable.getName(), "filter"), byte[].class);
        // select filter with the correct bloom term id and filter type id from the category table
        final SelectConditionStep<Record1<byte[]>> selectFilterStep = DSL
                .select(filterField)
                .from(categoryTable)
                .where(bloomTermCondition)
                .and(typeIdCondition);
        // function 'bloommatch' compares category table filter byte[] against bloom filter byte[]
        final Condition filterFieldCondition = DSL
                .function("bloommatch", Boolean.class, selectFilterStep.asField(), comparedTo.field("filter"))
                .eq(true);
        // null check allows SQL to optimize query
        final Condition notNullCondition = comparedTo.field("filter").isNotNull();
        return filterFieldCondition.and(notNullCondition);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CategoryTableCondition cast = (CategoryTableCondition) object;
        return comparedTo.equals(cast.comparedTo) && bloomTermCondition.equals(cast.bloomTermCondition)
                && typeIdCondition.equals(cast.typeIdCondition) && categoryTable.equals(cast.categoryTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparedTo, bloomTermCondition, typeIdCondition, categoryTable);
    }
}
