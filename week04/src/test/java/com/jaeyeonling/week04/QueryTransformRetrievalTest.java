package com.jaeyeonling.week04;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("query-transform")
class QueryTransformRetrievalTest extends RetrievalStrategyTest {}
