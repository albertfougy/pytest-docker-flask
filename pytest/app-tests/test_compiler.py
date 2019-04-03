def test_compiler(compiler):
    result = compiler.compile('always permit')

    assert result == {
        'attributes': [],
        'rules': [{
            'type': 'always',
            'decision': 'Permit'
        }]
    }
