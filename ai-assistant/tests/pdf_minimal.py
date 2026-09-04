"""
Construction d'un PDF minimal, pour les tests.

Écrire les octets à la main plutôt que d'ajouter une bibliothèque de génération
de PDF aux dépendances : le service n'en produit pas, il n'en lit que. Une
dépendance de test qui n'existe qu'en test finit toujours par manquer dans la CI.
"""


def pdf_avec_texte(pages: list[str]) -> bytes:
    """PDF valide dont chaque page porte les lignes de texte demandées."""
    objets: list[bytes] = []

    nb = len(pages)
    # 1 = catalogue, 2 = arbre des pages, 3 = police, puis 2 objets par page.
    ids_pages = [4 + 2 * i for i in range(nb)]

    objets.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    kids = b" ".join(b"%d 0 R" % i for i in ids_pages)
    objets.append(b"<< /Type /Pages /Kids [" + kids + b"] /Count %d >>" % nb)
    objets.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    for i, contenu in enumerate(pages):
        flux = b"BT /F1 11 Tf 14 TL 40 760 Td\n"
        for ligne in contenu.split("\n"):
            echappee = ligne.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")
            flux += b"(" + echappee.encode("latin-1", "replace") + b") Tj T*\n"
        flux += b"ET"

        objets.append(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            b"/Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>"
            % (ids_pages[i] + 1)
        )
        objets.append(b"<< /Length %d >>\nstream\n" % len(flux) + flux + b"\nendstream")

    sortie = bytearray(b"%PDF-1.4\n")
    decalages = []
    for numero, corps in enumerate(objets, start=1):
        decalages.append(len(sortie))
        sortie += b"%d 0 obj\n" % numero + corps + b"\nendobj\n"

    debut_xref = len(sortie)
    sortie += b"xref\n0 %d\n" % (len(objets) + 1)
    sortie += b"0000000000 65535 f \n"
    for decalage in decalages:
        sortie += b"%010d 00000 n \n" % decalage
    sortie += (
        b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"
        % (len(objets) + 1, debut_xref)
    )
    return bytes(sortie)


def pdf_sans_texte(nb_pages: int = 3) -> bytes:
    """PDF de pages vides — ce que rend un manuel photocopié puis scanné."""
    return pdf_avec_texte([""] * nb_pages)
