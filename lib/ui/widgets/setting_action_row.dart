import 'package:flutter/material.dart';

class SettingActionRow extends StatelessWidget {
  final String title;
  final void Function() onClick;
  final String value;

  const SettingActionRow({
    super.key,
    required this.title,
    required this.onClick,
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
          ElevatedButton(
            onPressed: onClick,
            child : Text(value),
          ),
        ],
      ),
    );
  }
}