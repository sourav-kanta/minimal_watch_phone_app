
import 'package:flutter/material.dart';

import '../../native_bridge/native_bridge.dart';
import 'destination_input.dart';

class NavigationWidget extends StatefulWidget {
  const NavigationWidget({super.key});

  @override
  State<NavigationWidget> createState() => _NavigationWidgetState();
}

class _NavigationWidgetState extends State<NavigationWidget> {
  GeocodingResult? _selectedDestination;
  bool _startingNavigation = false;
  bool _stoppingNavigation = false;

  Future<void> _startNavigation() async {
    final destination = _selectedDestination;

    if (destination == null || _startingNavigation) {
      return;
    }

    setState(() {
      _startingNavigation = true;
    });

    try {
      await NativeBleClient.startNavigation(
        latitude: destination.latitude,
        longitude: destination.longitude,
      );
    } catch (error, stackTrace) {
      debugPrint('Failed to start navigation: $error');
      debugPrintStack(stackTrace: stackTrace);

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Failed to start navigation.'),
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          _startingNavigation = false;
        });
      }
    }
  }

  Future<void> _stopNavigation() async {
    if (_stoppingNavigation) return;

    setState(() {
      _stoppingNavigation = true;
    });

    try {
      await NativeBleClient.stopNavigation();
    } catch (error, stackTrace) {
      debugPrint('Failed to stop navigation: $error');
      debugPrintStack(stackTrace: stackTrace);

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Failed to stop navigation.'),
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          _stoppingNavigation = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Navigation',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),

            DestinationSearchWidget(
              onDestinationSelected: (destination) {
                setState(() {
                  _selectedDestination = destination;
                });
              },
              onDestinationCleared: () {
                if (_selectedDestination != null) {
                  setState(() {
                    _selectedDestination = null;
                  });
                }
              },
            ),

            if (_selectedDestination != null) ...[
              const SizedBox(height: 8),
              Text(
                'Selected: ${_selectedDestination!.name}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],

            const SizedBox(height: 16),

            FilledButton.icon(
              onPressed: _selectedDestination != null &&
                      !_startingNavigation
                  ? _startNavigation
                  : null,
              icon: _startingNavigation
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(Icons.navigation),
              label: Text(
                _startingNavigation
                    ? 'Starting...'
                    : 'Start Navigation',
              ),
            ),

            const SizedBox(height: 8),

            OutlinedButton.icon(
              onPressed: _stoppingNavigation ? null : _stopNavigation,
              icon: _stoppingNavigation
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(Icons.stop),
              label: Text(
                _stoppingNavigation
                    ? 'Stopping...'
                    : 'Stop Navigation',
              ),
            ),
          ],
        ),
      ),
    );
  }
}
