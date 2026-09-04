package tn.wtm.school.planning.solver.ref;

import org.junit.jupiter.api.Test;
import tn.wtm.school.planning.solver.enums.RoomType;

import static org.assertj.core.api.Assertions.assertThat;

class RoomRefTest {

    @Test
    void normalRoomIsNotSpecial() {
        assertThat(room(RoomType.NORMALE).isSpecialRoom()).isFalse();
    }

    @Test
    void labRoomsAreSpecial() {
        assertThat(room(RoomType.LABPHYSIQUE).isSpecialRoom()).isTrue();
        assertThat(room(RoomType.LABSCIENCE).isSpecialRoom()).isTrue();
        assertThat(room(RoomType.TECH).isSpecialRoom()).isTrue();
        assertThat(room(RoomType.SALLESPORT).isSpecialRoom()).isTrue();
        assertThat(room(RoomType.LABINFORMATIQUE).isSpecialRoom()).isTrue();
        assertThat(room(RoomType.WORKSHOP).isSpecialRoom()).isTrue();
    }

    @Test
    void canAccommodateNullRequirementAcceptsAnyRoom() {
        assertThat(room(RoomType.NORMALE).canAccommodate(null)).isTrue();
        assertThat(room(RoomType.LABPHYSIQUE).canAccommodate(null)).isTrue();
    }

    @Test
    void canAccommodateNormalRequirementAcceptsAnyRoom() {
        assertThat(room(RoomType.NORMALE).canAccommodate(RoomType.NORMALE)).isTrue();
        assertThat(room(RoomType.LABPHYSIQUE).canAccommodate(RoomType.NORMALE)).isTrue();
    }

    @Test
    void canAccommodateSpecificRequirementNeedsExactType() {
        assertThat(room(RoomType.LABPHYSIQUE).canAccommodate(RoomType.LABPHYSIQUE)).isTrue();
        assertThat(room(RoomType.NORMALE).canAccommodate(RoomType.LABPHYSIQUE)).isFalse();
        assertThat(room(RoomType.LABSCIENCE).canAccommodate(RoomType.LABPHYSIQUE)).isFalse();
        assertThat(room(RoomType.SALLESPORT).canAccommodate(RoomType.SALLESPORT)).isTrue();
    }

    @Test
    void equalityBasedOnIdOnly() {
        RoomRef a = RoomRef.builder().id(1L).code("ph1").type(RoomType.LABPHYSIQUE).capacity(24).build();
        RoomRef b = RoomRef.builder().id(1L).code("ph2").type(RoomType.NORMALE).capacity(30).build();
        RoomRef c = RoomRef.builder().id(2L).code("ph1").type(RoomType.LABPHYSIQUE).capacity(24).build();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    private RoomRef room(RoomType type) {
        return RoomRef.builder().id(1L).code("R").type(type).capacity(30).build();
    }
}
