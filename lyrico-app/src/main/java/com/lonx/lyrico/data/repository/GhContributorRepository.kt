package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.GitHubContributor

interface GhContributorRepository {
    suspend fun getContributors(owner: String, repo: String): Result<List<GitHubContributor>>
}