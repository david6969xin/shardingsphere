/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.jdbc.core.datasource;

import com.dangdang.ddframe.rdb.sharding.api.MasterSlaveDataSourceFactory;
import com.dangdang.ddframe.rdb.sharding.api.config.ShardingRuleConfig;
import com.dangdang.ddframe.rdb.sharding.api.config.TableRuleConfig;
import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.constant.ShardingPropertiesConstant;
import com.dangdang.ddframe.rdb.sharding.executor.ExecutorEngine;
import com.dangdang.ddframe.rdb.sharding.rule.MasterSlaveRule;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class ShardingDataSourceTest {
    
    @Test(expected = IllegalStateException.class)
    public void assertGetDatabaseProductNameWhenDataBaseProductNameDifferent() throws SQLException {
        DataSource dataSource1 = mockDataSource("MySQL");
        DataSource dataSource2 = mockDataSource("H2");
        Map<String, DataSource> dataSourceMap = new HashMap<>(2, 1);
        dataSourceMap.put("ds1", dataSource1);
        dataSourceMap.put("ds2", dataSource2);
        assertDatabaseProductName(dataSourceMap, dataSource1.getConnection(), dataSource2.getConnection());
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertGetDatabaseProductNameWhenDataBaseProductNameDifferentForMasterSlave() throws SQLException {
        DataSource dataSource1 = mockDataSource("MySQL");
        DataSource masterDataSource = mockDataSource("H2");
        DataSource slaveDataSource = mockDataSource("H2");
        Map<String, DataSource> slaveDataSourceMap = new HashMap<>(1, 1);
        slaveDataSourceMap.put("slaveDataSource", slaveDataSource);
        MasterSlaveDataSource dataSource2 = (MasterSlaveDataSource) MasterSlaveDataSourceFactory.createDataSource(new MasterSlaveRule("ds", "masterDataSource", masterDataSource, slaveDataSourceMap));
        Map<String, DataSource> dataSourceMap = new HashMap<>(2, 1);
        dataSourceMap.put("ds1", dataSource1);
        dataSourceMap.put("ds2", dataSource2);
        assertDatabaseProductName(dataSourceMap, dataSource1.getConnection(), dataSource2.getMasterSlaveRule().getMasterDataSource().getConnection(), 
                dataSource2.getMasterSlaveRule().getSlaveDataSourceMap().get("slaveDataSource").getConnection());
    }
    
    @Test
    public void assertGetDatabaseProductName() throws SQLException {
        DataSource dataSource1 = mockDataSource("H2");
        DataSource dataSource2 = mockDataSource("H2");
        DataSource dataSource3 = mockDataSource("H2");
        Map<String, DataSource> dataSourceMap = new HashMap<>(3, 1);
        dataSourceMap.put("ds1", dataSource1);
        dataSourceMap.put("ds2", dataSource2);
        dataSourceMap.put("ds3", dataSource3);
        assertDatabaseProductName(dataSourceMap, dataSource1.getConnection(), dataSource2.getConnection(), dataSource3.getConnection());
    }
    
    @Test
    public void assertGetDatabaseProductNameForMasterSlave() throws SQLException {
        DataSource dataSource1 = mockDataSource("H2");
        DataSource masterDataSource = mockDataSource("H2");
        DataSource slaveDataSource = mockDataSource("H2");
        Map<String, DataSource> slaveDataSourceMap = new HashMap<>(1, 1);
        slaveDataSourceMap.put("slaveDataSource", slaveDataSource);
        MasterSlaveDataSource dataSource2 = (MasterSlaveDataSource) MasterSlaveDataSourceFactory.createDataSource(new MasterSlaveRule("ds", "masterDataSource", masterDataSource, slaveDataSourceMap));
        DataSource dataSource3 = mockDataSource("H2");
        Map<String, DataSource> dataSourceMap = new HashMap<>(3, 1);
        dataSourceMap.put("ds1", dataSource1);
        dataSourceMap.put("ds2", dataSource2);
        dataSourceMap.put("ds3", dataSource3);
        assertDatabaseProductName(dataSourceMap, dataSource1.getConnection(), dataSource2.getMasterSlaveRule().getMasterDataSource().getConnection(), 
                dataSource2.getMasterSlaveRule().getSlaveDataSourceMap().get("slaveDataSource").getConnection(), dataSource3.getConnection());
    }
    
    private void assertDatabaseProductName(final Map<String, DataSource> dataSourceMap, final Connection... connections) throws SQLException {
        try {
            createShardingDataSource(dataSourceMap).getDatabaseType();
        } finally {
            for (Connection each : connections) {
                verify(each).close();
            }
        }
    }
    
    private DataSource mockDataSource(final String dataBaseProductName) throws SQLException {
        DataSource result = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn(dataBaseProductName);
        when(result.getConnection()).thenReturn(connection);
        return result;
    }
    
    @Test
    public void assertGetConnection() throws SQLException {
        DataSource dataSource = mockDataSource("H2");
        Map<String, DataSource> dataSourceMap = new HashMap<>(1, 1);
        dataSourceMap.put("ds", dataSource);
        assertThat(createShardingDataSource(dataSourceMap).getConnection().getConnection("ds", SQLType.DQL), is(dataSource.getConnection()));
    }
    
    @Test
    public void assertRenewWithoutChangeExecutorPoolEngine() throws SQLException, NoSuchFieldException, IllegalAccessException {
        DataSource originalDataSource = mockDataSource("H2");
        Map<String, DataSource> originalDataSourceMap = new HashMap<>(1, 1);
        originalDataSourceMap.put("ds", originalDataSource);
        ShardingDataSource shardingDataSource = createShardingDataSource(originalDataSourceMap);
        ExecutorEngine originExecutorEngine = getExecutorEngine(shardingDataSource);
        DataSource newDataSource = mockDataSource("H2");
        Map<String, DataSource> newDataSourceMap = new HashMap<>(1, 1);
        newDataSourceMap.put("ds", newDataSource);
        shardingDataSource.renew(createShardingRuleConfig().build(newDataSourceMap), new Properties());
        assertThat(originExecutorEngine, is(getExecutorEngine(shardingDataSource)));
    }
    
    @Test
    public void assertRenewWithChangeExecutorEnginePoolSize() throws SQLException, NoSuchFieldException, IllegalAccessException {
        DataSource originalDataSource = mockDataSource("H2");
        Map<String, DataSource> originalDataSourceMap = new HashMap<>(1, 1);
        originalDataSourceMap.put("ds", originalDataSource);
        ShardingDataSource shardingDataSource = createShardingDataSource(originalDataSourceMap);
        final ExecutorEngine originExecutorEngine = getExecutorEngine(shardingDataSource);
        DataSource newDataSource = mockDataSource("H2");
        Map<String, DataSource> newDataSourceMap = new HashMap<>(1, 1);
        newDataSourceMap.put("ds", newDataSource);
        Properties props = new Properties();
        props.setProperty(ShardingPropertiesConstant.EXECUTOR_SIZE.getKey(), "100");
        shardingDataSource.renew(createShardingRuleConfig().build(newDataSourceMap), props);
        assertThat(originExecutorEngine, not(getExecutorEngine(shardingDataSource)));
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertRenewWithDatabaseTypeChanged() throws SQLException {
        DataSource originalDataSource = mockDataSource("H2");
        Map<String, DataSource> originalDataSourceMap = new HashMap<>(1, 1);
        originalDataSourceMap.put("ds", originalDataSource);
        ShardingDataSource shardingDataSource = createShardingDataSource(originalDataSourceMap);
        DataSource newDataSource = mockDataSource("MySQL");
        Map<String, DataSource> newDataSourceMap = new HashMap<>(1, 1);
        newDataSourceMap.put("ds", newDataSource);
        shardingDataSource.renew(createShardingRuleConfig().build(newDataSourceMap), new Properties());
    }
    
    private ShardingDataSource createShardingDataSource(final Map<String, DataSource> dataSourceMap) throws SQLException {
        return new ShardingDataSource(createShardingRuleConfig().build(dataSourceMap));
    }
    
    private ShardingRuleConfig createShardingRuleConfig() {
        ShardingRuleConfig result = new ShardingRuleConfig();
        TableRuleConfig tableRuleConfig = new TableRuleConfig();
        tableRuleConfig.setLogicTable("logicTable");
        tableRuleConfig.setActualTables("table_0, table_1, table_2");
        result.getTableRuleConfigs().add(tableRuleConfig);
        return result;
    }
    
    private ExecutorEngine getExecutorEngine(final ShardingDataSource shardingDataSource) throws NoSuchFieldException, IllegalAccessException {
        Field field = ShardingDataSource.class.getDeclaredField("executorEngine");
        field.setAccessible(true);
        return (ExecutorEngine) field.get(shardingDataSource);
    }
}
