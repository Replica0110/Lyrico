package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.dto.ContributorInfo

interface GhContributorRepository {
    suspend fun getContributors(owner: String, repo: String): Result<List<ContributorInfo>>
}