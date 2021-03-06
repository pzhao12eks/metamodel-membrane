/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.metamodel.membrane.controllers;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.apache.metamodel.membrane.app.InMemoryTenantRegistry;
import org.apache.metamodel.membrane.app.TenantRegistry;
import org.apache.metamodel.membrane.app.config.JacksonConfig;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TenantInteractionScenarioTest {

    private MockMvc mockMvc;

    @Before
    public void init() {
        final MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
        messageConverter.setObjectMapper(new JacksonConfig().objectMapper());

        final TenantRegistry tenantRegistry = new InMemoryTenantRegistry();
        final TenantController tenantController = new TenantController(tenantRegistry);
        final DataSourceController dataSourceController = new DataSourceController(tenantRegistry);
        final SchemaController schemaController = new SchemaController(tenantRegistry);
        final TableController tableController = new TableController(tenantRegistry);
        final ColumnController columnController = new ColumnController(tenantRegistry);
        final QueryController queryController = new QueryController(tenantRegistry);
        final TableDataController tableDataController = new TableDataController(tenantRegistry);

        mockMvc = MockMvcBuilders.standaloneSetup(tenantController, dataSourceController, schemaController,
                tableController, columnController, queryController, tableDataController).setMessageConverters(
                        messageConverter).build();
    }

    @Test
    public void testScenario() throws Exception {
        // create tenant
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/tenant1").contentType(
                    MediaType.APPLICATION_JSON)).andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("tenant", map.get("type"));
            assertEquals("tenant1", map.get("name"));
            assertEquals("[]", map.get("datasources").toString());
        }

        // create datasource
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.put("/tenant1/mydata").content(
                    "{'type':'pojo','database':'mydata','table-defs':'hello_world (greeting VARCHAR, who VARCHAR); foo (bar INTEGER, baz DATE);'}"
                            .replace('\'', '"')).contentType(MediaType.APPLICATION_JSON)).andExpect(
                                    MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("datasource", map.get("type"));
            assertEquals("mydata", map.get("name"));
            assertEquals(
                    "[{name=information_schema, uri=/tenant1/mydata/s/information_schema}, {name=mydata, uri=/tenant1/mydata/s/mydata}]",
                    map.get("schemas").toString());
        }

        // explore tenant - now with a datasource
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/tenant1")).andExpect(
                    MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("tenant", map.get("type"));
            assertEquals("tenant1", map.get("name"));
            assertEquals("[{name=mydata, uri=/tenant1/mydata}]", map.get("datasources").toString());
        }

        // explore schema
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/tenant1/mydata/s/mydata")).andExpect(
                    MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("schema", map.get("type"));
            assertEquals("mydata", map.get("name"));

            assertEquals(
                    "[{name=foo, uri=/tenant1/mydata/s/mydata/t/foo}, {name=hello_world, uri=/tenant1/mydata/s/mydata/t/hello_world}]",
                    map.get("tables").toString());
        }

        // explore table
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/tenant1/mydata/s/mydata/t/foo"))
                    .andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("table", map.get("type"));
            assertEquals("foo", map.get("name"));

            assertEquals("[{name=bar, uri=/tenant1/mydata/s/mydata/t/foo/c/bar}, "
                    + "{name=baz, uri=/tenant1/mydata/s/mydata/t/foo/c/baz}]", map.get("columns").toString());
        }

        // explore column
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/tenant1/mydata/s/mydata/t/foo/c/bar"))
                    .andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("column", map.get("type"));
            assertEquals("bar", map.get("name"));

            assertEquals("{number=0, nullable=true, primary-key=false, indexed=false, column-type=INTEGER}", map.get(
                    "metadata").toString());
        }

        // query metadata from information_schema
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/tenant1/mydata/q?sql={sql}",
                    "SELECT name, table FROM information_schema.columns")).andExpect(MockMvcResultMatchers.status()
                            .isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("dataset", map.get("type"));
            assertEquals("[columns.name, columns.table]", map.get("headers").toString());
            assertEquals("[[bar, foo], [baz, foo], [greeting, hello_world], [who, hello_world]]", map.get("data")
                    .toString());
        }

        // insert into table (x2)
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(
                    "/tenant1/mydata/s/mydata/t/hello_world/d").content("{'insert':[{'greeting':'Howdy','who':'MetaModel'}]}"
                            .replace('\'', '"')).contentType(MediaType.APPLICATION_JSON)).andExpect(
                                    MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("{status=ok}", map.toString());
        }
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(
                    "/tenant1/mydata/s/mydata/t/hello_world/d").content("{'insert':[{'greeting':'Hi','who':'Apache'}]}".replace(
                            '\'', '"')).contentType(MediaType.APPLICATION_JSON)).andExpect(MockMvcResultMatchers
                                    .status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("{status=ok}", map.toString());
        }

        // query the actual data
        // query metadata from information_schema
        {
            final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/tenant1/mydata/q?sql={sql}",
                    "SELECT greeting, who AS who_is_it FROM hello_world"))
                    .andExpect(MockMvcResultMatchers.status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            final Map<?, ?> map = new ObjectMapper().readValue(content, Map.class);
            assertEquals("dataset", map.get("type"));
            assertEquals("[hello_world.greeting, hello_world.who AS who_is_it]", map.get("headers").toString());
            assertEquals("[[Howdy, MetaModel], [Hi, Apache]]", map.get("data").toString());
        }
    }
}
