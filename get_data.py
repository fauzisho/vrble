import asyncio
from bleak import BleakScanner, BleakClient
from bleak.exc import BleakDeviceNotFoundError

# UUID from your Android app's BleGattServer.kt
SERVICE_UUID = "00001234-0000-1000-8000-00805f9b34fb"
FILE_NAME_CHAR_UUID = "00001235-0000-1000-8000-00805f9b34fb"
FILE_DATA_CHAR_UUID = "00001236-0000-1000-8000-00805f9b34fb"
FILE_SIZE_CHAR_UUID = "00001237-0000-1000-8000-00805f9b34fb"
CONTROL_CHAR_UUID = "00001238-0000-1000-8000-00805f9b34fb"


async def find_quest_device():
    """Scan and find Quest 3 device"""
    print("Scanning for Quest 3 BLE server...")
    devices = await BleakScanner.discover(timeout=5.0, return_adv=True)

    quest_device = None
    for address, (device, adv_data) in devices.items():
        name = device.name or "<Unknown>"
        if "Quest" in name or address == "9F5C44A9-E5AF-FFBC-A261-22273A89303D":
            print(f"✓ Found: {name} at {address}")
            quest_device = device
            break

    if not quest_device:
        print("✗ Quest 3 device not found!")
        return None

    return quest_device


async def read_file_info(client: BleakClient):
    """Read file name and size from Quest app"""
    try:
        # Read file name
        file_name_data = await client.read_gatt_char(FILE_NAME_CHAR_UUID)
        file_name = file_name_data.decode('utf-8')
        print(f"📄 File name: {file_name}")

        # Read file size
        file_size_data = await client.read_gatt_char(FILE_SIZE_CHAR_UUID)
        file_size = int(file_size_data.decode('utf-8'))
        print(f"📊 File size: {file_size} bytes")

        return file_name, file_size
    except Exception as e:
        print(f"Error reading file info: {e}")
        return None, None


async def start_file_transfer(client: BleakClient):
    """Send START command to begin file transfer"""
    try:
        await client.write_gatt_char(CONTROL_CHAR_UUID, b"START")
        print("▶️  Sent START command")
    except Exception as e:
        print(f"Error sending START: {e}")


async def read_file_data(client: BleakClient, file_size: int, chunk_size: int = 20):
    """Read file data in chunks"""
    file_data = bytearray()
    bytes_read = 0

    print(f"📥 Reading file data ({file_size} bytes)...")

    while bytes_read < file_size:
        try:
            chunk = await client.read_gatt_char(FILE_DATA_CHAR_UUID)
            file_data.extend(chunk)
            bytes_read += len(chunk)
            progress = (bytes_read / file_size) * 100
            print(f"Progress: {progress:.1f}% ({bytes_read}/{file_size} bytes)")

            if len(chunk) < chunk_size:
                # Last chunk received
                break
        except Exception as e:
            print(f"Error reading chunk: {e}")
            break

    return bytes(file_data)


async def save_file(filename: str, data: bytes):
    """Save received data to file"""
    try:
        with open(filename, 'wb') as f:
            f.write(data)
        print(f"✓ File saved: {filename}")
    except Exception as e:
        print(f"Error saving file: {e}")


async def connect_and_transfer():
    """Main function to connect and transfer file"""
    # Find Quest device
    quest_device = await find_quest_device()
    if not quest_device:
        print("Failed to find Quest device. Make sure:")
        print("  1. Quest 3 is connected via USB and ADB")
        print("  2. BLE Server is running in your Android app")
        print("  3. Placeholder file is loaded")
        return

    try:
        print(f"\n🔗 Connecting to {quest_device.address}...")
        async with BleakClient(quest_device) as client:
            print("✓ Connected!")

            # Read file info
            file_name, file_size = await read_file_info(client)
            if file_name is None:
                return

            # Start transfer
            await start_file_transfer(client)
            await asyncio.sleep(0.5)

            # Read file data
            file_data = await read_file_data(client, file_size)

            # Save to file
            await save_file(f"received_{file_name}", file_data)

            print("\n✓ Transfer complete!")
            print(f"Received {len(file_data)} bytes")

    except BleakDeviceNotFoundError:
        print(f"Device {quest_device.address} not found during connection")
    except Exception as e:
        print(f"Connection error: {e}")


async def continuous_monitoring():
    """Monitor Quest device and transfer on demand"""
    while True:
        try:
            print("\n" + "=" * 50)
            await connect_and_transfer()
        except KeyboardInterrupt:
            print("\nMonitoring stopped")
            break
        except Exception as e:
            print(f"Error: {e}")

        print("\nWaiting 10 seconds before next scan...")
        await asyncio.sleep(10)


if __name__ == "__main__":
    print("VR BLE File Transfer - Quest 3 Client")
    print("=" * 50)
    print("\nMake sure your Android app on Quest 3 has:")
    print("  ✓ BLE Server started")
    print("  ✓ Placeholder.txt loaded")
    print("\n" + "=" * 50 + "\n")

    try:
        # Single connection attempt
        asyncio.run(connect_and_transfer())

        # Or for continuous monitoring, use:
        # asyncio.run(continuous_monitoring())

    except KeyboardInterrupt:
        print("\nScript interrupted by user")
