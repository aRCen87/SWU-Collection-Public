"""Pure UI checks shared by the disposable-emulator scenarios."""
import re


def reader_page(root):
    # On Fold the collection stays visible beside the reader. Extension stock
    # counters belong to that collection, not to the card-reader navigation.
    scope = next((node for node in root.iter("node")
                  if node.attrib.get("resource-id") == "swu_detail_pane"), root)
    pages = [node.attrib.get("text", "") for node in scope.iter("node")
             if re.fullmatch(r"\d+ / \d+", node.attrib.get("text", ""))]
    assert len(pages) == 1, (pages, [node.attrib.get("text", "")
                                  for node in scope.iter("node") if node.attrib.get("text")][:60])
    index, total = map(int, pages[0].split(" / "))
    assert 1 <= index <= total, (index, total)
    return pages[0]
