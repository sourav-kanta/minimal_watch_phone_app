
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

class GeocodingResult {
  final String name;
  final double latitude;
  final double longitude;

  const GeocodingResult({
    required this.name,
    required this.latitude,
    required this.longitude,
  });
}

class DestinationSearchWidget extends StatefulWidget {
  final ValueChanged<GeocodingResult> onDestinationSelected;
  final VoidCallback? onDestinationCleared;

  const DestinationSearchWidget({
    super.key,
    required this.onDestinationSelected,
    this.onDestinationCleared,
  });

  @override
  State<DestinationSearchWidget> createState() =>
      _DestinationSearchWidgetState();
}

class _DestinationSearchWidgetState
    extends State<DestinationSearchWidget> {
  static const _minQueryLength = 5;
  static const _debounceDuration = Duration(milliseconds: 400);
  static const _requestTimeout = Duration(seconds: 15);

  final _controller = TextEditingController();

  Timer? _debounce;
  int _requestId = 0;

  List<GeocodingResult> _results = [];
  bool _loading = false;
  bool _hasSearched = false;
  String? _error;

  void _onQueryChanged(String value) {
    _debounce?.cancel();
    ++_requestId;

    widget.onDestinationCleared?.call();
    final query = value.trim();

    if (query.length < _minQueryLength) {
      setState(() {
        _results = [];
        _loading = false;
        _hasSearched = false;
        _error = null;
      });
      return;
    }

    setState(() {
      _results = [];
      _loading = true;
      _hasSearched = false;
      _error = null;
    });

    final requestId = _requestId;

    _debounce = Timer(
      _debounceDuration,
      () => _search(query, requestId),
    );
  }

  Future<void> _search(String query, int requestId) async {
    try {
      final uri = Uri.https(
        'photon.komoot.io',
        '/api/',
        {
          'q': query,
          'limit': '5',
          'lang': 'en',
        },
      );

      final response = await http.get(
        uri,
        headers: {
          'User-Agent':
              'MWWatchCompanion/0.1.0 (OpenStreetMap hobby navigation app)',
          'Accept': 'application/geo+json, application/json',
        },
      ).timeout(_requestTimeout);

      if (requestId != _requestId || !mounted) return;

      if (response.statusCode != 200) {
        throw Exception('HTTP ${response.statusCode}');
      }

      final data = jsonDecode(response.body) as Map<String, dynamic>;
      final features = data['features'] as List<dynamic>;

      final results = <GeocodingResult>[];

      for (final feature in features) {
        final item = feature as Map<String, dynamic>;
        final properties =
            item['properties'] as Map<String, dynamic>;
        final geometry =
            item['geometry'] as Map<String, dynamic>;
        final coordinates =
            geometry['coordinates'] as List<dynamic>;

        if (coordinates.length < 2) continue;

        final longitude = (coordinates[0] as num).toDouble();
        final latitude = (coordinates[1] as num).toDouble();

        if (!latitude.isFinite ||
            !longitude.isFinite ||
            latitude < -90 ||
            latitude > 90 ||
            longitude < -180 ||
            longitude > 180) {
          continue;
        }

        final nameParts = [
          properties['name'],
          properties['street'],
          properties['city'],
          properties['state'],
          properties['country'],
        ].whereType<String>()
            .where((part) => part.trim().isNotEmpty)
            .toSet()
            .toList();

        results.add(
          GeocodingResult(
            name: nameParts.join(', '),
            latitude: latitude,
            longitude: longitude,
          ),
        );
      }

      setState(() {
        _results = results;
        _loading = false;
        _hasSearched = true;
      });
    } catch (e, stackTrace) {
      if (requestId != _requestId || !mounted) return;

      debugPrint('Photon search failed: $e');
      debugPrintStack(stackTrace: stackTrace);

      setState(() {
        _results = [];
        _loading = false;
        _hasSearched = false;
        _error = 'Unable to search destinations. Try again.';
      });
    }
  }

  void _clearSearch() {
    _debounce?.cancel();
    ++_requestId;
    _controller.clear();

    widget.onDestinationCleared?.call();

    setState(() {
      _results = [];
      _loading = false;
      _hasSearched = false;
      _error = null;
    });
  }

  void _selectDestination(GeocodingResult result) {
    _debounce?.cancel();
    ++_requestId;

    _controller.text = result.name;

    setState(() {
      _results = [];
      _loading = false;
      _hasSearched = false;
      _error = null;
    });

    FocusScope.of(context).unfocus();
    widget.onDestinationSelected(result);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _controller,
          onChanged: _onQueryChanged,
          decoration: InputDecoration(
            hintText: 'Enter destination',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: _loading
                ? const Padding(
                    padding: EdgeInsets.all(14),
                    child: SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                      ),
                    ),
                  )
                : IconButton(
                    tooltip: 'Clear destination',
                    icon: const Icon(Icons.clear),
                    onPressed: _clearSearch,
                  ),
            border: const OutlineInputBorder(),
          ),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.all(12),
            child: Text(
              _error!,
              style: TextStyle(
                color: Theme.of(context).colorScheme.error,
              ),
            ),
          ),
        if (_hasSearched &&
            !_loading &&
            _error == null &&
            _results.isEmpty)
          const Padding(
            padding: EdgeInsets.all(12),
            child: Text('No destinations found.'),
          ),
        if (_results.isNotEmpty)
          Card(
            margin: const EdgeInsets.only(top: 4),
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _results.length,
              separatorBuilder: (_, __) =>
                  const Divider(height: 1),
              itemBuilder: (context, index) {
                final result = _results[index];

                return ListTile(
                  leading: const Icon(Icons.location_on_outlined),
                  title: Text(result.name),
                  subtitle: Text(
                    '${result.latitude.toStringAsFixed(5)}, '
                    '${result.longitude.toStringAsFixed(5)}',
                  ),
                  onTap: () => _selectDestination(result),
                );
              },
            ),
          ),
      ],
    );
  }
}
