package com.octotask.bot.ai;

import java.util.List;


public interface EmbeddingService {


    java.util.List<float[]> embed(java.util.List<String> texts) throws Exception;


    int getDimension();
}
