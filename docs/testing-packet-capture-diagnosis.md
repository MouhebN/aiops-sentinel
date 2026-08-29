# Testing Packet Capture Diagnosis

1. Install Wireshark CLI tools so `tshark` is available in `PATH`.
2. Start the backend, frontend, and FastAPI AI service.
3. Create or open an incident in AIOps Sentinel.
4. Capture traffic with Wireshark or `tcpdump`, then save it as `.pcap` or `.pcapng`.
5. In the incident detail page, use `Upload packet capture` and select the file.
6. Review the generated summary in the `Packet Capture Diagnosis` section.

Examples:

- Capture with `tcpdump`:
  ```bash
  sudo tcpdump -i any -w sample.pcap -c 200
  ```
- Verify `tshark`:
  ```bash
  tshark -v
  ```

If FastAPI returns `tshark is not installed. Please install Wireshark CLI tools.`, install the Wireshark CLI package on the host running the FastAPI service.
