package com.rapii.snapje.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rapii.snapje.R
import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoRepositoryInterface
import com.rapii.snapje.data.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search screen for finding photos by name.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPhotoClick: (PhotoItem, List<PhotoItem>) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val searchState by viewModel.searchState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val gridState = rememberLazyGridState()
    var lastTappedPhotoIndex by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchTextField(
                        query = searchQuery,
                        onQueryChange = viewModel::updateQuery,
                        onSearch = { /* handled by debounce */ },
                        placeholder = stringResource(R.string.search_photos)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                searchState.error != null -> {
                    EmptySearchState(
                        message = searchState.error ?: "Unknown error",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                searchState.photos.isEmpty() && searchQuery.isEmpty() -> {
                    EmptySearchState(
                        message = stringResource(R.string.search_photos),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                searchState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                searchState.photos.isEmpty() -> {
                    EmptySearchState(
                        message = stringResource(R.string.no_photos_found),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    SearchResultsGrid(
                        photos = searchState.photos,
                        state = gridState,
                        onPhotoClick = { photo, index ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastTappedPhotoIndex = index
                            onPhotoClick(photo, searchState.photos)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch() }
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun EmptySearchState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchResultsGrid(
    photos: List<PhotoItem>,
    state: LazyGridState,
    onPhotoClick: (PhotoItem, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        state = state,
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = photos,
            key = { it.id }
        ) { photo ->
            val index = photos.indexOf(photo)
            PhotoGridItem(
                photo = photo,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onPhotoClick(photo, index) }
            )
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: PhotoItem,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(photo.uri)
            .crossfade(true)
            .build(),
        contentDescription = photo.displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val photos: List<PhotoItem> = emptyList(),
    val query: String = "",
    val error: String? = null
)

/**
 * ViewModel for search functionality.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val photoRepository: PhotoRepositoryInterface
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collect { query ->
                    if (query.isNotBlank()) {
                        performSearch(query)
                    } else {
                        _searchState.value = SearchUiState()
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        _searchQuery.value = query
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                _searchState.value = _searchState.value.copy(isLoading = true)
                val result = photoRepository.searchPhotos(query)
                _searchState.value = SearchUiState(
                    isLoading = false,
                    photos = result.photos,
                    query = result.query
                )
            } catch (e: Exception) {
                _searchState.value = SearchUiState(
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
}
