import 'package:flutter/material.dart';

class SettingRow extends StatelessWidget {
  final String title;
  final String value;

  const SettingRow({
    super.key,
    required this.title,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 14,
      ),
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: Colors.white12,
          ),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(title),
          ),
          Text(value),
        ],
      ),
    );
  }
}