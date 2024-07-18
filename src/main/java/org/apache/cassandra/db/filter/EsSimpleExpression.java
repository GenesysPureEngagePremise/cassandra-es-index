package org.apache.cassandra.db.filter;

import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.filter.RowFilter.Expression;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.schema.TableMetadata;

public class EsSimpleExpression extends RowFilter.SimpleExpression {
  private Expression original;

  public EsSimpleExpression(Expression expression) {
    super(expression.column, expression.operator, expression.value);
    this.original = expression;
  }

  @Override
  public boolean isSatisfiedBy(TableMetadata metadata, DecoratedKey partitionKey, Row row) {
    return true;
  }

  @Override
  public String toString() {
    return original.toString();
  }

  @Override
  protected Kind kind() {
    return original.kind();
  }
}
