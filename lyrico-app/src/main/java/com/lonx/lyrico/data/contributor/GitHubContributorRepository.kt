package com.lonx.lyrico.data.contributor

interface GitHubContributorRepository {
    suspend fun getContributors(owner: String, repo: String): Result<List<ContributorInfo>>
}
