package com.jaeyeonling.stage2.m2;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("reranker")
class ReRankingRetrievalTest extends RetrievalStrategyTest {}
